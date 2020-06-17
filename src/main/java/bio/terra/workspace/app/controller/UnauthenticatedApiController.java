package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.UnauthenticatedApi;
import bio.terra.workspace.generated.model.SystemStatus;
import bio.terra.workspace.service.status.WorkspaceManagerStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {

  private WorkspaceManagerStatusService statusService;

  @Autowired
  public UnauthenticatedApiController(WorkspaceManagerStatusService statusService) {
    this.statusService = statusService;
  }

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    SystemStatus currentStatus = statusService.getCurrentStatus();
    return new ResponseEntity<>(
        currentStatus, currentStatus.getOk() ? HttpStatus.valueOf(200) : HttpStatus.valueOf(500));
  }
}
