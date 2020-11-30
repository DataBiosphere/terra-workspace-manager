package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.McTerraApi;
import bio.terra.workspace.generated.model.CreateGoogleContextRequestBody;
import bio.terra.workspace.generated.model.JobModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.net.URI;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class McTerraController implements McTerraApi {

  private SamService samService;
  private WorkspaceService workspaceService;
  private JobService jobService;
  private AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public McTerraController(
      SamService samService,
      WorkspaceService workspaceService,
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.jobService = jobService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<Void> addRole(
      @PathVariable("id") UUID id,
      @PathVariable("role") bio.terra.workspace.generated.model.IamRole role,
      @PathVariable("memberEmail") String memberEmail) {
    samService.addWorkspaceRole(
        getAuthenticatedInfo(), id, IamRole.fromApiModel(role), memberEmail);
    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<Void> removeRole(
      @PathVariable("id") UUID id,
      @PathVariable("role") bio.terra.workspace.generated.model.IamRole role,
      @PathVariable("memberEmail") String memberEmail) {
    samService.removeWorkspaceRole(
        getAuthenticatedInfo(), id, IamRole.fromApiModel(role), memberEmail);
    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<JobModel> createGoogleContext(
      UUID id, @Valid CreateGoogleContextRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    // TODO(PF-153): Use the optional jobId from the body for idempotency instead of always creating
    // a new job id.
    String jobId = workspaceService.createGoogleContext(id, userReq);
    JobModel jobModel = jobService.retrieveJob(jobId, userReq);
    // TODO(PF-221): Fix the jobs polling location once it exists.
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .location(URI.create(String.format("/api/jobs/v1/%s", jobId)))
        .body(jobModel);
  }

  @Override
  public ResponseEntity<Void> deleteGoogleContext(UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    workspaceService.deleteGoogleContext(id, userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
