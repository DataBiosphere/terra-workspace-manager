package bio.terra.workspace.service.resource.controlled.flight.clone;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveGcsBucketCloudAttributesStep;

public class CloneControlledResourceFlight extends Flight {

  public CloneControlledResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    final ControlledResource sourceResource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Flight Plan
    // 1. Gather controlled resource metadata for source object
    // 2. Gather creation parameters from existing object
    // 3. Launch sub-flight to create appropriate resource
    // 4. Copy data across resources (future)
    // 5. Build result object

    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));
    switch (sourceResource.getResourceType()) {
      case GCS_BUCKET:
        addBucketSteps(flightBeanBag, sourceResource.castToGcsBucketResource());
        break;
      case AI_NOTEBOOK_INSTANCE:
      case DATA_REPO_SNAPSHOT:
      case BIG_QUERY_DATASET:
      default:
        throw new BadRequestException(
            String.format(
                "Clone resource not implemented for type %s", sourceResource.getResourceType()));
    }
  }

  private void addBucketSteps(
      FlightBeanBag flightBeanBag, ControlledGcsBucketResource sourceResource) {
    addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            sourceResource, flightBeanBag.getCrlService(), flightBeanBag.getWorkspaceService()));
    //    addStep(new CopyGcsBucketDefinitionStep());
    //    addStep(new CopyGcsBucketDataStep());
    //    addStep(new SetCloneResponseStep());

  }
}
