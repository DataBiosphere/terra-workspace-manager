package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FolderKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Clones all folders from source workspace to destination workspace. */
public class CloneAllFoldersStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CloneAllFoldersStep.class);
  private final FolderDao folderDao;

  public CloneAllFoldersStep(FolderDao folderDao) {
    this.folderDao = folderDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(), JobMapKeys.REQUEST.getKeyName());
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(), ControlledResourceKeys.SOURCE_WORKSPACE_ID);
    var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(JobMapKeys.REQUEST.getKeyName(), Workspace.class)
            .getWorkspaceId();

    // Create and clone all folders
    ImmutableList<Folder> sourceFolders = folderDao.listFoldersInWorkspace(sourceWorkspaceId);
    // Use Map<String, String> rather than Map<UUID, UUID> to avoid JSON deserialization error
    Map<String, String> folderIdMap = new HashMap<>();

    if (sourceFolders == null) {
      logger.info("Source workspace {} doesn't have any folders to copy", sourceWorkspaceId);
      return StepResult.getStepResultSuccess();
    }
    for (Folder sourceFolder : sourceFolders) {
      // folderId is primary key, so can't reuse source folder ID
      UUID destinationFolderId = UUID.randomUUID();
      folderDao.createFolder(
          new Folder(
              destinationFolderId,
              destinationWorkspaceId,
              sourceFolder.displayName(),
              sourceFolder.description(),
              // Don't set parentFolderId because parent folder might not have been created yet.
              // parentFolderId will be set below.
              /*parentFolderId=*/ null,
              sourceFolder.properties()));
      folderIdMap.put(sourceFolder.id().toString(), destinationFolderId.toString());
    }
    // Update the cloned folders' parent folder id
    for (Folder sourceFolder : sourceFolders) {
      if (sourceFolder.parentFolderId() != null) {
        folderDao.updateFolder(
            destinationWorkspaceId,
            UUID.fromString(folderIdMap.get(sourceFolder.id().toString())),
            /*displayName=*/ null,
            /*description=*/ null,
            UUID.fromString(folderIdMap.get(sourceFolder.parentFolderId().toString())),
            /*updateParent=*/ true);
      }
    }

    logger.info(
        "Cloned all folders relations {} in workspace {} to new workspace {}",
        sourceFolders,
        sourceWorkspaceId,
        destinationWorkspaceId);

    context.getWorkingMap().put(FolderKeys.FOLDER_IDS_TO_CLONE_MAP, folderIdMap);

    return StepResult.getStepResultSuccess();
  }

  // Delete the folders and rows in the folder table
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    var destinationWorkspaceId =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    folderDao.deleteAllFolders(destinationWorkspaceId);
    return StepResult.getStepResultSuccess();
  }
}
