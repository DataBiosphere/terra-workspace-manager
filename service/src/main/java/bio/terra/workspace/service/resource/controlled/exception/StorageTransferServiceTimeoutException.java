package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.ErrorReportException;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

public class StorageTransferServiceTimeoutException extends ErrorReportException {

  public StorageTransferServiceTimeoutException(String message) {
    super(message);
  }

  public StorageTransferServiceTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

  public StorageTransferServiceTimeoutException(Throwable cause) {
    super(cause);
  }

  public StorageTransferServiceTimeoutException(Throwable cause, HttpStatus statusCode) {
    super(cause, statusCode);
  }

  public StorageTransferServiceTimeoutException(
      String message, @Nullable List<String> causes, @Nullable HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public StorageTransferServiceTimeoutException(
      String message,
      Throwable cause,
      @Nullable List<String> causes,
      @Nullable HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }
}
