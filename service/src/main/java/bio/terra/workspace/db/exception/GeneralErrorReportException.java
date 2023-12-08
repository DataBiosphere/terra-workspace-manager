package bio.terra.workspace.db.exception;

import bio.terra.common.exception.ErrorReportException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * This exception is not intended to be thrown. It is a constructable form of ErrorReportException
 * that can be safely serialized and deserialized from the error columns in the database.
 *
 * <p>NOTE: this class is serialized to JSON, so take care modifying it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralErrorReportException extends ErrorReportException {
  public GeneralErrorReportException(String message) {
    super(message);
  }

  public GeneralErrorReportException(
      String message,
      Throwable cause,
      @Nullable List<String> causes,
      @Nullable HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }
}
