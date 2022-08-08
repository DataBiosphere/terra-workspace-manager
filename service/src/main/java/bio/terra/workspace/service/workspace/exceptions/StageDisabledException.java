package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;

/** Exception for when an operation on workspace is not allowed according to its stage. */
public class StageDisabledException extends BadRequestException {
  public StageDisabledException(UUID workspaceUuid, WorkspaceStage stage, String operationName) {
    super(
        String.format(
            "'%s' not allowed for workspace %s in stage %s.", operationName, workspaceUuid, stage));
  }

  public StageDisabledException(String message) {
    super(message);
  }
}
