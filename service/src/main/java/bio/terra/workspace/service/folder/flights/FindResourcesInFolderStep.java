package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ReferencedResourceKeys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FindResourcesInFolderStep implements Step {

  private final UUID workspaceId;
  private final UUID folderId;
  private final FolderDao folderDao;
  private final ResourceDao resourceDao;

  public FindResourcesInFolderStep(
      UUID workspaceId, UUID folderId, FolderDao folderDao, ResourceDao resourceDao) {
    this.workspaceId = workspaceId;
    this.folderId = folderId;
    this.folderDao = folderDao;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Set<UUID> folderIds = new HashSet<>();
    getAllSubFolderIds(folderId, folderIds);
    var offset = 0;
    var limit = 100;
    List<WsmResource> batch;
    List<WsmResource> controlledResources = new ArrayList<>();
    List<WsmResource> referencedResources = new ArrayList<>();
    do {
      batch = resourceDao.enumerateResources(workspaceId, null, null, offset, limit);
      offset += limit;
      batch.stream()
          .filter(resource -> isInFolder(resource, folderIds))
          .forEach(
              resource -> {
                if (StewardshipType.REFERENCED == resource.getStewardshipType()) {
                  referencedResources.add(resource);
                } else if (StewardshipType.CONTROLLED == resource.getStewardshipType()) {
                  controlledResources.add(resource);
                }
              });
    } while (batch.size() == limit);
    context.getWorkingMap().put(ControlledResourceKeys.RESOURCES_TO_DELETE, controlledResources);
    context.getWorkingMap().put(ReferencedResourceKeys.RESOURCES_TO_DELETE, referencedResources);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private void getAllSubFolderIds(UUID folderId, Set<UUID> folderIds) {
    folderIds.add(folderId);
    List<Folder> subFolders = folderDao.listFolders(workspaceId, folderId);
    if (subFolders.isEmpty()) {
      return;
    }
    for (Folder f : subFolders) {
      getAllSubFolderIds(f.id(), folderIds);
    }
  }

  private static boolean isInFolder(WsmResource resource, Set<UUID> folderIds) {
    return resource.getProperties().containsKey(FOLDER_ID_KEY)
        && folderIds.contains(UUID.fromString(resource.getProperties().get(FOLDER_ID_KEY)));
  }
}
