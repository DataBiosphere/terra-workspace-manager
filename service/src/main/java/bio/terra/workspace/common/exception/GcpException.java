package bio.terra.workspace.common.exception;

import bio.terra.common.exception.ErrorReportException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.springframework.http.HttpStatus;

/**
 * An {@code ErrorReportException} wrapper around errors thrown by GCP. This is used to surface GCP
 * errors directly to users.
 */
public class GcpException extends ErrorReportException {

  public GcpException(GoogleJsonResponseException cause) {
    super(cause, HttpStatus.resolve(cause.getStatusCode()));
  }
}
