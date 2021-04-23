package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleFixedInterval;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateServiceAccountStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.GenerateServiceAccountIdStep;
import bio.terra.workspace.service.workspace.flight.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {
  /**
   * Retry rule for Notebook GCP steps. If GCP is down, we don't know when it will be back, so don't
   * wait forever. Note that RetryRules can be re-used within but not across Flight instances.
   */
  private final RetryRule notebookGcpRetryRule =
      new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount=  */ 10);

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
        addStep(
            new CreateGcsBucketStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getWorkspaceService()));
        addStep(
            new GcsBucketCloudSyncStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getWorkspaceService()));
        break;
      case AI_NOTEBOOK_INSTANCE:
        addStep(new GenerateServiceAccountIdStep());
        addStep(
            new CreateServiceAccountStep(
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService(),
                resource.castToAiNotebookInstanceResource()),
                notebookGcpRetryRule);
        addStep(
            new CreateAiNotebookInstanceStep(
                flightBeanBag.getCrlService(),
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getWorkspaceService()),
                notebookGcpRetryRule);
        // TODO(PF-469): Set permissions on service account and notebook instances.
        break;
      case BIG_QUERY_DATASET:
      default:
        throw new IllegalStateException(
            String.format("Unrecognized resource type %s", resource.getResourceType()));
    }
    // Populate the return response
    addStep(new SetCreateResponseStep(resource));
  }
}
