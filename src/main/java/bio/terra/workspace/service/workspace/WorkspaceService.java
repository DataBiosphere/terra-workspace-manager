package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageEnumModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import io.opencensus.contrib.spring.aop.Traced;
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

  @Traced
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
    if (body.getSpendProfile() != null) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, body.getSpendProfile());
    }

    if (body.getStage() != null) {
      createJob.addParameter(WorkspaceFlightMapKeys.WORKSPACE_STAGE, body.getStage());
    } else {
      createJob.addParameter(
          WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStageEnumModel.RAWLS_WORKSPACE);
    }

    return createJob.submitAndWait(CreatedWorkspace.class);
  }

  @Traced
  public WorkspaceDescription getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return workspaceDao.getWorkspace(id);
  }

  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userReq) {

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
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, id);
    deleteJob.submitAndWait(null);
  }
}
