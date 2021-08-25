package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

/**
 * This flight uses a dynamic list of steps depending on ControlledResourceKeys.RESOURCES_TO_CLONE
 * in the input parameters list.
 */
public class CloneAllResourcesFlight extends Flight {

  public CloneAllResourcesFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    final List<ResourceWithFlightId> resourcesAndIds =
        inputParameters.get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});

    // Each entry in the list corresponds to a new step in this flight
    for (ResourceWithFlightId resourceWithFlightId : resourcesAndIds) {
      addFlightLaunchStepsForResource(resourceWithFlightId, flightBeanBag);
    }
  }

  private void addFlightLaunchStepsForResource(
      ResourceWithFlightId resourceWithFlightId, FlightBeanBag flightBeanBag) {
    switch (resourceWithFlightId.getResource().getStewardshipType()) {
      case REFERENCED:
        // TODO: we may just want a single step for this
        addStep(new LaunchCloneReferenceResourceFlightStep(
            flightBeanBag.getReferencedResourceService(),
            resourceWithFlightId.getResource().castToReferencedResource(),
            resourceWithFlightId.getFlightId()));
      case CONTROLLED:
        switch (resourceWithFlightId.getResource().getResourceType()) {
          case GCS_BUCKET:
            addStep(new LaunchCloneGcsBucketResourceFlightStep(
                resourceWithFlightId
                    .getResource()
                    .castToControlledResource()
                    .castToGcsBucketResource(),
                resourceWithFlightId.getFlightId()));
            addStep(new AwaitCloneGcsBucketResourceFlightStep(resourceWithFlightId.getResource().castToControlledResource()
                .castToGcsBucketResource(), resourceWithFlightId.getFlightId()));
          case BIG_QUERY_DATASET:
            addStep(new LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
                resourceWithFlightId
                    .getResource()
                    .castToControlledResource()
                    .castToBigQueryDatasetResource(),
                resourceWithFlightId.getFlightId()));
            addStep(new AwaitCloneControlledGcpBigQueryDatasetResourceFlightStep(
                resourceWithFlightId.getResource().castToControlledResource().castToBigQueryDatasetResource(),
                resourceWithFlightId.getFlightId()));
          case DATA_REPO_SNAPSHOT:
          case AI_NOTEBOOK_INSTANCE:
          default:
            throw new InternalLogicException(
                String.format(
                    "Unsupported controlled resource type %s",
                    resourceWithFlightId.getResource().getResourceType()));
        }
      default:
        throw new InternalLogicException(
            String.format(
                "Unsupported stewardship type %s",
                resourceWithFlightId.getResource().getStewardshipType()));
    }
  }
}
