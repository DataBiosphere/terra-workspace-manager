package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import java.util.UUID;

/**
 * Exception for a workspace operation that needs a spend profile when the workspace has no spend
 * profile associated with it.
 */
public class MissingSpendProfileException extends BadRequestException {
  public MissingSpendProfileException(String message) {
    super(message);
  }

  public static MissingSpendProfileException forWorkspace(UUID workspaceId) {
    return new MissingSpendProfileException(
        String.format(
            "Spend profile id required, but none found on workspace %s", workspaceId.toString()));
  }
}
