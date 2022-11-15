package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneAllFoldersStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloneSourceMetadata;
import bio.terra.workspace.service.workspace.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.UUID;

public class CloneFoldersStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CloneFoldersStep.class);

  private final FolderDao folderDao;
  private final CloneSourceMetadata cloneSourceMetadata;
  private final Workspace destinationWorkspace;
  private final Map<UUID, UUID> folderIdMap;

  public CloneFoldersStep(
    FolderDao folderDao,
    CloneSourceMetadata cloneSourceMetadata,
    Workspace destinationWorkspace,
    Map<UUID, UUID> folderIdMap) {
    this.folderDao = folderDao;
    this.cloneSourceMetadata = cloneSourceMetadata;
    this.destinationWorkspace = destinationWorkspace;
    this.folderIdMap = folderIdMap;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Generate the map from destination folder id to destination folder object
    Map<UUID, Folder> sourceFolders = cloneSourceMetadata.getFolders();

    for (Folder sourceFolder : sourceFolders.values()) {
      var destinationFolder =
        new Folder(
          folderIdMap.get(sourceFolder.id()),
          destinationWorkspace.getWorkspaceId(),
          sourceFolder.displayName(),
          sourceFolder.description(),
          (sourceFolder.parentFolderId() != null
            ? folderIdMap.get(sourceFolder.parentFolderId())
            : null),
          sourceFolder.properties());

      try {
        folderDao.createFolder(destinationFolder);
      } catch (DuplicateKeyException e) {
        // If this step is restarted, some of the folders will be already in the destination
        // workspace. We ignore duplicate keys to make this idempotent.
      }
    }
    return StepResult.getStepResultSuccess();
  }

  // Delete all of the folders in the database
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    folderDao.deleteAllFolders(destinationWorkspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }

}
