package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceService {

  private JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;

  @Autowired
  public WorkspaceService(JobService jobService, WorkspaceDao workspaceDao, SamService samService) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
  }

  public void createWorkspace(CreateWorkspaceRequestBody body, AuthenticatedUserRequest userReq) {

    // CreatedWorkspace workspace = new CreatedWorkspace();
    UUID workspaceId = body.getId();
    String description = "Create workspace " + workspaceId.toString();
    JobControl jobControl = body.getJobControl();
    // TODO: Need to support pubsub notifications here.
    JobBuilder createJob =
        jobService
            .newJob(description, jobControl.getJobid(), WorkspaceCreateFlight.class, body, userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    if (body.getSpendProfile().isPresent()) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, body.getSpendProfile().get());
    }
    createJob.submit();
  }

  public WorkspaceDescription getWorkspace(String id, AuthenticatedUserRequest userReq) {

    if (!samService.isAuthorized(
        userReq.getRequiredToken(),
        SamUtils.SAM_WORKSPACE_RESOURCE,
        id,
        SamUtils.SAM_WORKSPACE_READ_ACTION)) {
      throw new SamUnauthorizedException(
          "User " + userReq.getEmail() + " is not allowed to read workspace " + id);
    }

    WorkspaceDescription result = workspaceDao.getWorkspace(id);
    return result;
  }
}
