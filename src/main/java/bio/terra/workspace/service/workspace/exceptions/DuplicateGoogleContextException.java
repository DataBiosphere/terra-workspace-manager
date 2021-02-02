package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.ConflictException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Exception thrown when attempting to create a Google context in a workspace which already has one.
 */
public class DuplicateGoogleContextException extends ConflictException {
  public DuplicateGoogleContextException(@NotNull UUID workspaceId) {
    super(String.format("Workspace %s already has a Google context.", workspaceId.toString()));
  }
}
