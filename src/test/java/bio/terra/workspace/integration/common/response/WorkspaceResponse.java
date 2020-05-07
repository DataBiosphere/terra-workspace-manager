package bio.terra.workspace.integration.common.response;

import bio.terra.workspace.generated.model.ErrorReport;
import java.util.Optional;
import org.springframework.http.HttpStatus;

public class WorkspaceResponse<T> {

  private ObjectOrErrorResponse<ErrorReport, T> response;

  public WorkspaceResponse(ObjectOrErrorResponse<ErrorReport, T> response) {
    this.response = response;
  }

  public HttpStatus getStatusCode() {
    return response.getStatusCode();
  }

  public Optional<ErrorReport> getErrorObject() {
    return response.getErrorObject();
  }

  public Optional<T> getResponseObject() {
    return response.getResponseObject();
  }
}
