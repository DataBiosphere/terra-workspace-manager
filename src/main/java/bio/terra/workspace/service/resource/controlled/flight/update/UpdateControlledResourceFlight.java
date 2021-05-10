package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;

public class UpdateControlledResourceFlight extends Flight {

  public UpdateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // update metadata step

    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new UpdateGcsBucketStep(
                resource.castToGcsBucketResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService()));
        break;

      case AI_NOTEBOOK_INSTANCE:
      case BIG_QUERY_DATASET:
      default:
        // These types update local metadata only, and need no flight.
        break;
    }
  }
}
