package bio.terra.workspace.integration.common.response;

import java.util.Optional;
import org.springframework.http.HttpStatus;

public class ObjectOrErrorResponse<S, T> {

  private HttpStatus statusCode;
  private Optional<S> errorObject;
  private Optional<T> responseObject;

  public HttpStatus getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(HttpStatus statusCode) {
    this.statusCode = statusCode;
  }

  public Optional<S> getErrorObject() {
    return errorObject;
  }

  public void setErrorModel(Optional<S> errorObject) {
    this.errorObject = errorObject;
  }

  public Optional<T> getResponseObject() {
    return responseObject;
  }

  public void setResponseObject(Optional<T> responseObject) {
    this.responseObject = responseObject;
  }
}
