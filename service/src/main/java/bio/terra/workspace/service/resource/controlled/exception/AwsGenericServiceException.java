package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.ErrorReportException;
import java.util.List;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * A generic exception used as an ErrorReportException-based wrapper around errors from AWS. When
 * possible, prefer throwing a more specific exception instead.
 */
public class AwsGenericServiceException extends ErrorReportException {
  public AwsGenericServiceException(String message, AwsServiceException awsException) {
    super(
        message + awsException.getMessage(),
        awsException,
        List.of(awsException.getCause().getMessage()),
        HttpStatus.resolve(awsException.statusCode()));
  }
}
