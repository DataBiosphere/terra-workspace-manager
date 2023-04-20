package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.ErrorReportException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Exception thrown when a user attempts to delete a workspace that still has children
 */
public class ChildrenBlockingDeletionException extends ErrorReportException {

  public ChildrenBlockingDeletionException(String message, List<String> causes) {
    super(message, causes, HttpStatus.CONFLICT);
  }

}
