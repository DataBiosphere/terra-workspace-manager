package bio.terra.workspace.integration.common.response;

import io.vavr.control.Either;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;

public class ErrorOrObjectResponse<S, T> {

  private HttpStatus statusCode;
  private Either<S, T> data;

  public HttpStatus getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(HttpStatus statusCode) {
    this.statusCode = statusCode;
  }

  public Either<S, T> getData() {
    return data;
  }

  public void setErrorObject(S left) {
    data = Either.left(left);
  }

  public void setResponseObject(T right) {
    data = Either.right(right);
  }

  public S getErrorObject() throws NoSuchElementException {
    return data.getLeft();
  }

  public T getResponseObject() throws NoSuchElementException {
    return data.get();
  }

  public boolean isErrorObject() {
    return data.isLeft();
  }

  public boolean isResponseObject() {
    return data.isRight();
  }
}
