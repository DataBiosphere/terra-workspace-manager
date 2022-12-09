package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ErrorReportException;
import com.azure.core.management.exception.ManagementException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Wraps {@link ManagementException} in an Exception that can be [de]serialized by {@link
 * bio.terra.workspace.service.job.StairwayExceptionSerializer}. Use this to bubble Azure errors out
 * of Stairway flights.
 */
public class AzureManagementException extends ErrorReportException {

  // required by StairwayExceptionSerializer
  public AzureManagementException(String message, List<String> causes, HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public AzureManagementException(ManagementException cause) {
    super(cause, ManagementExceptionUtils.getHttpStatus(cause).orElse(null));
  }
}
