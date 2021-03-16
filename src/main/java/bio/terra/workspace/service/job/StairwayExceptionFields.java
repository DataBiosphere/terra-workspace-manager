package bio.terra.workspace.service.job;

import java.util.List;

// POJO for serializing one level of exception into JSON
public class StairwayExceptionFields {
  private boolean isApiErrorReportException;
  private String className;
  private String message;
  private List<String> errorDetails;
  private int errorCode;

  public boolean isApiErrorReportException() {
    return isApiErrorReportException;
  }

  public StairwayExceptionFields setApiErrorReportException(boolean errorReportException) {
    isApiErrorReportException = errorReportException;
    return this;
  }

  public String getClassName() {
    return className;
  }

  public StairwayExceptionFields setClassName(String className) {
    this.className = className;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public StairwayExceptionFields setMessage(String message) {
    this.message = message;
    return this;
  }

  public List<String> getErrorDetails() {
    return errorDetails;
  }

  public StairwayExceptionFields setErrorDetails(List<String> errorDetails) {
    this.errorDetails = errorDetails;
    return this;
  }

  public int getErrorCode() {
    return errorCode;
  }

  public StairwayExceptionFields setErrorCode(int errorCode) {
    this.errorCode = errorCode;
    return this;
  }
}
