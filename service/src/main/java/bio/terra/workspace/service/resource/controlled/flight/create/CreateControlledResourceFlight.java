package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.petserviceaccount.model.UserWithPetSa;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.CreateAzureDiskStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.GetAzureDiskStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.CreateAzureIpStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.GetAzureIpStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.CreateAzureNetworkStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.GetAzureNetworkStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.CreateAzureStorageStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.GetAzureStorageStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.CreateAzureVmStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.GetAzureVmStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.CreateGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsBucketCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  private final RetryRule gcpRetryRule = RetryRules.cloud();
  private final RetryRule dbRetryRule = RetryRules.shortDatabase();

  public CreateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    FlightUtils.validateRequiredEntries(
        inputParameters, ResourceKeys.RESOURCE, JobMapKeys.AUTH_USER_INFO.getKeyName());

    final ControlledResource resource =
        inputParameters.get(ResourceKeys.RESOURCE, ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Role is optionally populated for private resources
    final ControlledResourceIamRole privateResourceIamRole =
        inputParameters.get(
            ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLE, ControlledResourceIamRole.class);

    final String assignedUserEmail = resource.getAssignedUser().orElse(null);

    // Store the resource metadata in the WSM database. Doing this first means concurrent
    // conflicting resources with the same name or resource attributes can be prevented.
    addStep(new StoreMetadataStep(flightBeanBag.getResourceDao()), dbRetryRule);

    // create the Sam resource associated with the resource
    addStep(
        new CreateSamResourceStep(
            flightBeanBag.getSamService(),
            resource,
            privateResourceIamRole,
            assignedUserEmail,
            userRequest));

    // Get the cloud context and store it in the working map
    switch (resource.getResourceType().getCloudPlatform()) {
      case AZURE:
      case ANY:
        // TODO: pull cloud context from flight into step parallel to GCP
        break;
      case GCP:
        // This step may need to update the cloud context row in the database to convert
        // context V1 format into V2 format.
        addStep(
            new GetGcpCloudContextStep(
                resource.getWorkspaceId(), flightBeanBag.getGcpCloudContextService(), userRequest),
            dbRetryRule);
        break;
    }

    // create the cloud resource and grant IAM roles via CRL
    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new CreateGcsBucketStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        addStep(
            new GcsBucketCloudSyncStep(
                flightBeanBag.getControlledResourceService(),
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getGcpCloudContextService(),
                userRequest),
            gcpRetryRule);
        break;
      case AI_NOTEBOOK_INSTANCE:
        {
          UserWithPetSa userAndPet =
              new UserWithPetSa(
                  assignedUserEmail,
                  inputParameters.get(
                      ControlledResourceKeys.NOTEBOOK_PET_SERVICE_ACCOUNT, String.class));
          addNotebookSteps(
              userAndPet, flightBeanBag, resource.castToAiNotebookInstanceResource(), userRequest);
          break;
        }
      case BIG_QUERY_DATASET:
        // Unlike other resources, BigQuery datasets set IAM permissions at creation time to avoid
        // unwanted defaults from GCP.
        addStep(
            new CreateBigQueryDatasetStep(
                flightBeanBag.getControlledResourceService(),
                flightBeanBag.getCrlService(),
                resource.castToBigQueryDatasetResource(),
                flightBeanBag.getGcpCloudContextService(),
                userRequest),
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
      case AZURE_NETWORK:
        addStep(
            new GetAzureNetworkStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureNetworkResource()),
            RetryRules.cloud());
        addStep(
            new CreateAzureNetworkStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureNetworkResource()),
            RetryRules.cloud());
        break;
      case AZURE_VM:
        addStep(
            new GetAzureVmStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureVmResource()),
            RetryRules.cloud());
        addStep(
            new CreateAzureVmStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureVmResource(),
                flightBeanBag.getResourceDao()),
            RetryRules.cloud());
        break;
      case AZURE_STORAGE_ACCOUNT:
        addStep(
            new GetAzureStorageStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureStorageResource()),
            RetryRules.cloud());
        addStep(
            new CreateAzureStorageStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                resource.castToAzureStorageResource()),
            RetryRules.cloud());
        break;
      default:
        throw new IllegalStateException(
            String.format("Unrecognized resource type %s", resource.getResourceType()));
    }
    // Update private_resource_state from INITIALIZING to ACTIVE, if this is a private resource.
    addStep(
        new MarkPrivateResourceReadyStep(resource, flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());
    // Populate the return response
    addStep(new SetCreateResponseStep(resource));
  }

  private void addNotebookSteps(
      UserWithPetSa userAndPet,
      FlightBeanBag flightBeanBag,
      ControlledAiNotebookInstanceResource resource,
      AuthenticatedUserRequest userRequest) {
    addStep(
        new RetrieveNetworkNameStep(
            flightBeanBag.getCrlService(), resource, flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
    addStep(
        new GrantPetUsagePermissionStep(
            resource.getWorkspaceId(), userAndPet, flightBeanBag.getPetSaService()),
        gcpRetryRule);
    addStep(
        new CreateAiNotebookInstanceStep(
            resource,
            userAndPet,
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
    addStep(
        new NotebookCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            resource,
            flightBeanBag.getGcpCloudContextService(),
            userRequest),
        gcpRetryRule);
  }
}
