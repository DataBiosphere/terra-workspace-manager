package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ErrorReportException;
import com.azure.core.management.exception.ManagementException;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

public class AzureManagementException extends ErrorReportException {

  public AzureManagementException(ManagementException cause) {
    super(cause, getHttpStatus(cause));
  }

  @Nullable
  private static HttpStatus getHttpStatus(ManagementException cause) {
    try {
      return HttpStatus.valueOf(cause.getResponse().getStatusCode());
    } catch (Throwable t) {
      return null;
    }
  }
}
