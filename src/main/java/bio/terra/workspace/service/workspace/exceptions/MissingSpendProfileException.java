package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;
import java.util.UUID;

/**
 * Exception for a workspace operation that needs a spend profile when the workspace has no spend
 * profile associated with it.
 */
public class MissingSpendProfileException extends BadRequestException {
  public MissingSpendProfileException(UUID workspaceId) {
    super(
        String.format(
            "Spend profile id required, but none found on workspace %s", workspaceId.toString()));
  }
}
