package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This flight uses a dynamic list of steps depending on ControlledResourceKeys.RESOURCES_TO_CLONE
 * in the input parameters list. Each resource type requires a different subflight to be launched.
 */
public class CloneAllResourcesFlight extends Flight {

  private static final Logger logger = LoggerFactory.getLogger(CloneAllResourcesFlight.class);

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
    final WsmResource resource = resourceWithFlightId.getResource();

    switch (resource.getStewardshipType()) {
      case REFERENCED:
        addStep(
            new LaunchCreateReferenceResourceFlightStep(
                flightBeanBag.getReferencedResourceService(),
                resourceWithFlightId.getResource().castToReferencedResource(),
                resourceWithFlightId.getFlightId()));
        addStep(
            new AwaitCreateReferenceResourceFlightStep(
                resourceWithFlightId.getResource().castToReferencedResource(),
                resourceWithFlightId.getFlightId(),
                flightBeanBag.getResourceDao()),
            RetryRules.cloudLongRunning());
        break;
      case CONTROLLED:
        switch (resourceWithFlightId.getResource().getResourceType()) {
          case CONTROLLED_GCP_GCS_BUCKET:
            addStep(
                new LaunchCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceWithFlightId.getFlightId()));
            addStep(
                new AwaitCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceWithFlightId.getFlightId()),
                RetryRules.cloudLongRunning());
            break;
          case CONTROLLED_GCP_BIG_QUERY_DATASET:
            addStep(
                new LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceWithFlightId.getFlightId()));
            addStep(
                new AwaitCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceWithFlightId.getFlightId()),
                RetryRules.cloudLongRunning());
            break;
          case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE:
          default:
            // Can't throw in a flight constructor
            logger.error(
                "Unsupported controlled resource type {}",
                resourceWithFlightId.getResource().getResourceType());
            break;
        }
        break;
      default:
        logger.error(
            "Unsupported stewardship type {}",
            resourceWithFlightId.getResource().getStewardshipType());
        break;
    }
  }
}
