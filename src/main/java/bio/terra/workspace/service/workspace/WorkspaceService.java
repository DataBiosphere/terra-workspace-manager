package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.MDCUtils;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import brave.Span;
import brave.Tracer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceService {

  private JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final MDCUtils mdcUtils;
  @Autowired private final Tracer tracer;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      Tracer tracer,
      MDCUtils mdcUtils) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.tracer = tracer;
    this.mdcUtils = mdcUtils;
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
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
            .addParameter(WorkspaceFlightMapKeys.MDC_KEY, mdcUtils.serializeCurrentMdc());
    if (body.getSpendProfile() != null) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, body.getSpendProfile());
    }
    return createJob.submitAndWait(CreatedWorkspace.class);
  }

  @NewSpan
  public WorkspaceDescription getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    Span newSpan = tracer.nextSpan().name("workspaceAuthz");
    // this try is here to demonstrate one way to create a span. When adding tracing properly,
    // switch to using @NewSpan on the workspaceAuth method.
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(newSpan.start())) {
      samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_READ_ACTION);
    } finally {
      newSpan.finish();
    }
    WorkspaceDescription result = workspaceDao.getWorkspace(id);
    return result;
  }

  public void deleteWorkspace(UUID id, String userToken) {

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
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, id)
            .addParameter(WorkspaceFlightMapKeys.MDC_KEY, mdcUtils.serializeCurrentMdc());
    deleteJob.submitAndWait(null);
  }
}
