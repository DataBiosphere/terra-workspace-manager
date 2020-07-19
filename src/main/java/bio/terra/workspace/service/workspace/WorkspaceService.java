package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.trace.StackdriverTrace;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import io.opencensus.common.Scope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceService {

  private JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final StackdriverTrace trace;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      StackdriverTrace trace) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.trace = trace;
  }

  public CreatedWorkspace createWorkspace(
      CreateWorkspaceRequestBody body, AuthenticatedUserRequest userReq) {

    UUID workspaceId = body.getId();
    String description = "Create workspace " + workspaceId.toString();
    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(), // JobId does not need persistence for sync calls.
                WorkspaceCreateFlight.class,
                body,
                userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    if (body.getSpendProfile().isPresent()) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, body.getSpendProfile().get());
    }
    return createJob.submitAndWait(CreatedWorkspace.class);
  }

  public WorkspaceDescription getWorkspace(String id, AuthenticatedUserRequest userReq) {
    try (Scope s = trace.scope("getWorkspace")) {
      try (Scope ss = trace.scope("workspaceAuthz")) {
        samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_READ_ACTION);
      }
      try (Scope ss = trace.scope("workspaceDao.getWorkspace")) {
        WorkspaceDescription result = workspaceDao.getWorkspace(id);
        return result;
      }
    }
  }

  public void deleteWorkspace(String id, String userToken) {

    AuthenticatedUserRequest userReq = new AuthenticatedUserRequest().token(Optional.of(userToken));
    samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_DELETE_ACTION);

    String description = "Delete workspace " + id;
    JobBuilder deleteJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                WorkspaceDeleteFlight.class,
                null, // Delete does not have a useful request body
                userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.fromString(id));
    deleteJob.submitAndWait(null);
  }
}
