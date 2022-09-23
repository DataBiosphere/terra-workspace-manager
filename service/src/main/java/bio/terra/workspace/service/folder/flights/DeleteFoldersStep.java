package bio.terra.workspace.service.folder.flights;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.FolderDao;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DeleteFoldersStep implements Step {

  private final FolderDao folderDao;
  private final UUID folderId;
  private final UUID workspaceId;

  public DeleteFoldersStep(FolderDao folderDao, UUID workspaceId, UUID folderId) {
    this.folderDao = folderDao;
    this.folderId = folderId;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    boolean deleted = folderDao.deleteFolder(workspaceId, folderId);
    FlightUtils.setResponse(context, deleted, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
