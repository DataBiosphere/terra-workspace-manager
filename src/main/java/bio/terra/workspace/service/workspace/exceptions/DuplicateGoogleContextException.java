package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;
import bio.terra.workspace.common.exception.ErrorReportException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to create a Google context in a workspace which already
 * has one.
 */
public class DuplicateGoogleContextException extends ErrorReportException {
  private static final HttpStatus thisStatus = HttpStatus.BAD_REQUEST;
  public DuplicateGoogleContextException(UUID workspaceId) {
    super(
        String.format(
            "Spend profile id required, but none found on workspace %s", workspaceId.toString()));
  }
}
