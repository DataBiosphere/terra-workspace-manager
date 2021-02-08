package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.ErrorReportException;
import bio.terra.workspace.generated.model.ErrorReport;
import org.springframework.http.HttpStatus;

/** A common utility for building an ErrorReport from an exception. */
public class ErrorReportUtils {

  public static ErrorReport buildErrorReport(Exception exception) {
    if (exception instanceof ErrorReportException) {
      ErrorReportException errorReport = (ErrorReportException) exception;
      return new ErrorReport()
          .message(errorReport.getMessage())
          .statusCode(errorReport.getStatusCode().value())
          .causes(errorReport.getCauses());
    } else {
      return new ErrorReport()
          .message(exception.getMessage())
          .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
          .causes(null);
    }
  }
}
