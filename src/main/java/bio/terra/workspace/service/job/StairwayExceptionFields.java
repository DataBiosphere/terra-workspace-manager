package bio.terra.workspace.service.job;

import java.util.List;
import org.jetbrains.annotations.NotNull;

// POJO for serializing one level of exception into JSON
public class StairwayExceptionFields {
  private boolean isErrorReportException;
  private String className;
  private String message;
  private List<String> errorDetails;
  private int errorCode;

  public boolean isErrorReportException() {
    return isErrorReportException;
  }

  public @NotNull StairwayExceptionFields setErrorReportException(boolean errorReportException) {
    isErrorReportException = errorReportException;
    return this;
  }

  public String getClassName() {
    return className;
  }

  public @NotNull StairwayExceptionFields setClassName(String className) {
    this.className = className;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public @NotNull StairwayExceptionFields setMessage(String message) {
    this.message = message;
    return this;
  }

  public List<String> getErrorDetails() {
    return errorDetails;
  }

  public @NotNull StairwayExceptionFields setErrorDetails(List<String> errorDetails) {
    this.errorDetails = errorDetails;
    return this;
  }

  public int getErrorCode() {
    return errorCode;
  }

  public @NotNull StairwayExceptionFields setErrorCode(int errorCode) {
    this.errorCode = errorCode;
    return this;
  }
}
