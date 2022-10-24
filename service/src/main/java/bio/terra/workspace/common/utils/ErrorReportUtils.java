package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.generated.model.ApiErrorReport;
import org.springframework.http.HttpStatus;

/** A common utility for building an ApiErrorReport from an exception. */
public class ErrorReportUtils {

  public static ApiErrorReport buildApiErrorReport(Exception exception) {
    if (exception instanceof ErrorReportException errorReport) {
      return new ApiErrorReport()
          .message(errorReport.getMessage())
          .statusCode(errorReport.getStatusCode().value())
          .causes(errorReport.getCauses());
    } else {
      return new ApiErrorReport()
          .message(exception.getMessage())
          .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
          .causes(null);
    }
  }
}
