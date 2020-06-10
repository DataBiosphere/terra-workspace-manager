package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.UnauthenticatedApi;
import bio.terra.workspace.generated.model.SystemStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {

  @Override
  public ResponseEntity<SystemStatus> status() {
    // TODO: this is not a very good status endpoint, but is useful for testing the proxy
    return new ResponseEntity<>(HttpStatus.valueOf(200));
  }
}
