package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;
import bio.terra.workspace.common.model.Workspace;

/** Exception for when an operation on workspace is not allowed according to its stage. */
public class StageDisabledException extends BadRequestException {
  public StageDisabledException(Workspace workspace, String operationName) {
    super(
        String.format(
            "'%s' not allowed for workspace %s in stage %s.",
            operationName, workspace.workspaceId(), workspace.workspaceStage()));
  }
}
