package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRoleList;
import bio.terra.workspace.service.job.JobMapKeys;
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

    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final List<ControlledResourceIamRole> privateResourceIamRoles =
        inputParameters.get(
            ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLES, ControlledResourceIamRoleList.class);

    // store the resource metadata in the WSM database
    addStep(new StoreMetadataStep(flightBeanBag.getResourceDao()));

    // create the Sam resource associated with the resource
    addStep(
        new CreateSamResourceStep(
            flightBeanBag.getSamService(), resource, privateResourceIamRoles, userRequest));

    // get google group names from Sam groups and store them in the working map
    addStep(
        new SyncSamGroupsStep(
            flightBeanBag.getSamService(), resource.getWorkspaceId(), userRequest));

    // create the cloud resource and grant IAM roles via CRL
    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new CreateGcsBucketStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getWorkspaceService()));
        addStep(
            new GrantGcsBucketIamRolesStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getWorkspaceService()));
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
