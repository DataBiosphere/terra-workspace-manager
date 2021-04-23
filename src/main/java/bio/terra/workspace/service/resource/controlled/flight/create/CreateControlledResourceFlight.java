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
import bio.terra.workspace.service.workspace.flight.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  public CreateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    // These numbers are arbitrarily selected as a reasonable backoff timer for intermittent issues,
    // as we should not hold a flight forever in case of a longer cloud outage.
    RetryRule cloudRetryRule = new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount= */ 6);

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
      case BIG_QUERY_DATASET:
        addStep(
            new CreateBigQueryDatasetStep(
                flightBeanBag.getCrlService(), resource.castToBigQueryDatasetResource()),
            cloudRetryRule);
        break;
      default:
        throw new IllegalStateException(
            String.format("Unrecognized resource type %s", resource.getResourceType()));
    }
    // Populate the return response
    addStep(new SetCreateResponseStep(resource));
  }
}
