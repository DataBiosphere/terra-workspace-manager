package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateServiceAccountStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.GenerateServiceAccountIdStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.ServiceAccountPolicyStep;
import bio.terra.workspace.service.workspace.flight.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  private final RetryRule gcpRetryRule = RetryRules.cloud();

  public CreateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // Stairway does not provide a way to specify parameterized types for deserialization
    @SuppressWarnings("unchecked")
    final List<ControlledResourceIamRole> privateResourceIamRoles =
        inputParameters.get(ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLES, List.class);

    // store the resource metadata in the WSM database
    addStep(new StoreMetadataStep(flightBeanBag.getResourceDao()));

    // create the Sam resource associated with the resource
    addStep(
        new CreateSamResourceStep(
            flightBeanBag.getSamService(), resource, privateResourceIamRoles, userRequest));

    // get google group names for workspace roles from Sam and store them in the working map
    addStep(
        new SyncSamGroupsStep(
            flightBeanBag.getSamService(), resource.getWorkspaceId(), userRequest));
    // get google group names for resource policies from Sam. These are only used for individual
    // access (i.e. private resource users and applications). This step should also run for
    // application-managed resources once those are supported.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      addStep(new SyncResourceSamGroupsStep(flightBeanBag.getSamService(), resource, userRequest));
    }

    // create the cloud resource and grant IAM roles via CRL
    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        // TODO(PF-589): apply gcpRetryRule to these steps once they are idempotent.
        addStep(
            new CreateGcsBucketStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getGcpCloudContextService()));
        addStep(
            new GcsBucketCloudSyncStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getGcpCloudContextService()));
        break;
      case AI_NOTEBOOK_INSTANCE:
        addNotebookSteps(
            userRequest,
            flightBeanBag,
            resource.castToAiNotebookInstanceResource(),
            privateResourceIamRoles);
        break;
      case BIG_QUERY_DATASET:
        // Unlike other resources, BigQuery datasets set IAM permissions at creation time to avoid
        // unwanted defaults from GCP.
        addStep(
            new CreateBigQueryDatasetStep(
                flightBeanBag.getCrlService(),
                resource.castToBigQueryDatasetResource(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        break;

      case AZURE_DISK:
        addStep(
            new GetAzureDiskStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureDiskResource()),
            RetryRules.cloud());
        addStep(
            new CreateAzureDiskStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureDiskResource()),
            RetryRules.cloud());
        break;
      case AZURE_IP:
        addStep(
            new GetAzureIpStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureIpResource()),
            RetryRules.cloud());
        addStep(
            new CreateAzureIpStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureIpResource()),
            RetryRules.cloud());
        break;
      default:
        throw new IllegalStateException(
            String.format("Unrecognized resource type %s", resource.getResourceType()));
    }
    // Populate the return response
    addStep(new SetCreateResponseStep(resource));
  }

  private void addNotebookSteps(
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag,
      ControlledAiNotebookInstanceResource resource,
      List<ControlledResourceIamRole> privateResourceIamRoles) {
    addStep(
        new RetrieveNetworkNameStep(
            flightBeanBag.getCrlService(), resource, flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
    addStep(new GenerateServiceAccountIdStep());
    addStep(
        new CreateServiceAccountStep(
            flightBeanBag.getCrlService(), flightBeanBag.getGcpCloudContextService(), resource),
        gcpRetryRule);
    addStep(
        new ServiceAccountPolicyStep(
            userRequest,
            flightBeanBag.getCrlService(),
            resource,
            flightBeanBag.getSamService(),
            flightBeanBag.getGcpCloudContextService(),
            privateResourceIamRoles),
        gcpRetryRule);
    addStep(
        new CreateAiNotebookInstanceStep(
            flightBeanBag.getCrlService(), resource, flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
    addStep(
        new NotebookCloudSyncStep(
            flightBeanBag.getCrlService(), resource, flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
  }
}
