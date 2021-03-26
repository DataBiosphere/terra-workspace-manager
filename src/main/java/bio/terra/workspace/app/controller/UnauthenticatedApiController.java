package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.VersionConfiguration;
import bio.terra.workspace.generated.controller.UnauthenticatedApi;
import bio.terra.workspace.generated.model.ApiSystemStatus;
import bio.terra.workspace.generated.model.ApiSystemVersion;
import bio.terra.workspace.service.status.WorkspaceManagerStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {

  private final WorkspaceManagerStatusService statusService;
  private final ApiSystemVersion currentVersion;

  @Autowired
  public UnauthenticatedApiController(
      WorkspaceManagerStatusService statusService, VersionConfiguration versionConfiguration) {
    this.statusService = statusService;

    this.currentVersion =
        new ApiSystemVersion()
            .gitTag(versionConfiguration.getGitTag())
            .gitHash(versionConfiguration.getGitHash())
            .github(
                "https://github.com/DataBiosphere/terra-workspace-manager/commit/"
                    + versionConfiguration.getGitHash())
            .build(versionConfiguration.getBuild());
  }

  @Override
  public ResponseEntity<ApiSystemStatus> serviceStatus() {
    ApiSystemStatus currentStatus = statusService.getCurrentStatus();
    return new ResponseEntity<>(
        currentStatus, currentStatus.isOk() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Override
  public ResponseEntity<ApiSystemVersion> serviceVersion() {
    return new ResponseEntity<>(currentVersion, HttpStatus.OK);
  }
}
