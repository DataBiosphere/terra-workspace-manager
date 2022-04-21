package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;

/** A workspace with this workspace_id already exists. */
public class DuplicateWorkspaceException extends BadRequestException {
  public DuplicateWorkspaceException(String message) {
    super(message);
  }

  public DuplicateWorkspaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
