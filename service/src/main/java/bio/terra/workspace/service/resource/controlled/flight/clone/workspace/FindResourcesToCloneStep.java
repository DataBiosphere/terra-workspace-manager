package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate a list of resource, flightID pairs for future steps to use. Each one will use the
 * (cloneable) resource and possibly the flightID.
 */
public class FindResourcesToCloneStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(FindResourcesToCloneStep.class);
  private final ResourceDao resourceDao;

  public FindResourcesToCloneStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(), ControlledResourceKeys.SOURCE_WORKSPACE_ID);
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), WorkspaceFlightMapKeys.FolderKeys.FOLDER_IDS_TO_CLONE_MAP);
    var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    HashMap<String, String> folderIdMap =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.FolderKeys.FOLDER_IDS_TO_CLONE_MAP,
                new TypeReference<>() {});
    int offset = 0;
    int limit = 100;
    List<WsmResource> batch;
    List<ResourceCloneInputs> result = new ArrayList<>();
    do {
      batch = resourceDao.enumerateResources(sourceWorkspaceId, null, null, offset, limit);
      offset += limit;
      List<WsmResource> cloneableResources =
          batch.stream().filter(FindResourcesToCloneStep::isCloneable).toList();

      for (WsmResource resource : cloneableResources) {
        String folderId = resource.getProperties().get(FOLDER_ID_KEY);
        result.add(
            new ResourceCloneInputs(
                resource,
                context.getStairway().createFlightId(),
                /*destinationResourceId=*/ UUID.randomUUID(),
                folderId != null ? UUID.fromString(folderIdMap.get(folderId)) : null));
      }

    } while (batch.size() == limit);

    // sort the resources by stewardship type reversed, so reference types go first
    result.sort(
        Comparator.comparing(
            r -> r.getResource().getStewardshipType().toString(), Comparator.reverseOrder()));
    logger.info(
        "Will clone resources with stewardship types {}",
        result.stream()
            .map(r -> r.getResource().getStewardshipType().toString())
            .collect(Collectors.joining(", ")));
    context.getWorkingMap().put(ControlledResourceKeys.RESOURCES_TO_CLONE, result);
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.RESOURCES_TO_CLONE);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo; no side effects.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private static boolean isCloneable(WsmResource resource) {
    return StewardshipType.REFERENCED == resource.getStewardshipType()
        || (StewardshipType.CONTROLLED == resource.getStewardshipType()
            && (WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE == resource.getResourceType()
                || WsmResourceType.CONTROLLED_GCP_GCS_BUCKET == resource.getResourceType()
                || WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET == resource.getResourceType()
                || WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER == resource.getResourceType()
                || WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY
                    == resource.getResourceType()));
  }
}
