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
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    List<ResourceCloneInputs> resourceCloneInputsList =
        inputParameters.get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});

    // Each entry in the list corresponds to a new step in this flight
    for (ResourceCloneInputs resourceCloneInputs : resourceCloneInputsList) {
      addFlightLaunchStepsForResource(resourceCloneInputs, flightBeanBag);
    }
  }

  private void addFlightLaunchStepsForResource(
      ResourceCloneInputs resourceCloneInputs, FlightBeanBag flightBeanBag) {
    WsmResource resource = resourceCloneInputs.getResource();

    switch (resource.getStewardshipType()) {
      case REFERENCED:
        addStep(
<<<<<<< HEAD
            new LaunchCreateReferenceResourceFlightStep(
                flightBeanBag.getSamService(),
=======
            new CloneReferencedResourceStep(
                flightBeanBag.getResourceDao(),
>>>>>>> main
                resourceCloneInputs.getResource().castToReferencedResource(),
                resourceCloneInputs.getDestinationResourceId(),
                resourceCloneInputs.getDestinationFolderId()),
            RetryRules.shortDatabase());
        break;
      case CONTROLLED:
        switch (resourceCloneInputs.getResource().getResourceType()) {
          case CONTROLLED_GCP_GCS_BUCKET:
            addStep(
                new LaunchCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()));
            addStep(
                new AwaitCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceCloneInputs.getFlightId()),
                RetryRules.cloudLongRunning());
            break;
          case CONTROLLED_GCP_BIG_QUERY_DATASET:
            addStep(
                new LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()));
            addStep(
                new AwaitCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceCloneInputs.getFlightId()),
                RetryRules.cloudLongRunning());
            break;
          case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE:
          default:
            // Can't throw in a flight constructor
            logger.error(
                "Unsupported controlled resource type {}",
                resourceCloneInputs.getResource().getResourceType());
            break;
        }
        break;
      default:
        logger.error(
            "Unsupported stewardship type {}",
            resourceCloneInputs.getResource().getStewardshipType());
        break;
    }
  }
}
