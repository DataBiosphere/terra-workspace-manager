package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateGoogleContextException;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.flight.*;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for workspace lifecycle operations.
 *
 * <p>This service holds core workspace management operations like creating, reading, and deleting
 * workspaces as well as their cloud contexts. New methods generally should go in new services.
 */
@Component
public class WorkspaceService {

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final SpendProfileService spendProfileService;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      SpendProfileService spendProfileService) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @Traced
  public UUID createWorkspace(WorkspaceRequest workspaceRequest, AuthenticatedUserRequest userReq) {

    String description = "Create workspace " + workspaceRequest.workspaceId().toString();
    JobBuilder createJob =
        jobService
            .newJob(
                description, workspaceRequest.jobId(), WorkspaceCreateFlight.class, null, userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceRequest.workspaceId());
    if (workspaceRequest.spendProfileId().isPresent()) {
      createJob.addParameter(
          WorkspaceFlightMapKeys.SPEND_PROFILE_ID, workspaceRequest.spendProfileId().get().id());
    }

    createJob.addParameter(
        WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspaceRequest.workspaceStage());

    return createJob.submitAndWait(UUID.class, true);
  }

  /**
   * Convenience function that checks existence of a workspace, followed by an authorization check
   * against that workspace.
   *
   * <p>Throws WorkspaceNotFoundException from getWorkspace if the workspace does not exist,
   * regardless of the user's permission.
   *
   * <p>Throws SamUnauthorizedException if the user is not permitted to perform the specified action
   * on the workspace in question.
   *
   * <p>Returns the Workspace object if it exists and the user is permitted to perform the specified
   * action.
   *
   * @param userReq the user's authenticated request
   * @param workspaceId id of the workspace in question
   * @param action the action to authorize against the workspace
   * @return the workspace, if it exists and the user is permitted to perform the specified action.
   */
  @Traced
  public Workspace authorizedGetWorkspace(
      AuthenticatedUserRequest userReq, UUID workspaceId, String action) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    samService.workspaceAuthzOnly(userReq, workspaceId, action);
    return workspace;
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    return authorizedGetWorkspace(userReq, id, SamConstants.SAM_WORKSPACE_READ_ACTION);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    authorizedGetWorkspace(userReq, id, SamConstants.SAM_WORKSPACE_DELETE_ACTION);

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
    deleteJob.submitAndWait(null, false);
  }

  /** Retrieves the cloud context of a workspace. */
  @Traced
  public WorkspaceCloudContext getCloudContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    authorizedGetWorkspace(userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return workspaceDao.getCloudContext(workspaceId);
  }

  /** Start a job to create a Google cloud context for the workspace. Returns the job id. */
  @Traced
  public String createGoogleContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    Workspace workspace =
        authorizedGetWorkspace(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    workspaceDao.assertMcWorkspace(workspace, "createGoogleContext");
    if (!workspaceDao.getCloudContext(workspaceId).googleProjectId().isEmpty()) {
      throw new DuplicateGoogleContextException(workspaceId);
    }
    Optional<SpendProfileId> spendProfileId = workspace.spendProfileId();
    if (spendProfileId.isEmpty()) {
      throw new MissingSpendProfileException(workspaceId);
    }
    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId.get(), userReq);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw new NoBillingAccountException(spendProfileId.get());
    }

    String jobId = UUID.randomUUID().toString();
    jobService
        .newJob(
            "Create Google Context " + workspaceId,
            jobId,
            CreateGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .addParameter(
            WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendProfile.billingAccountId().get())
        .submit(false);
    return jobId;
  }

  /** Delete the Google cloud context for the workspace. */
  @Traced
  public void deleteGoogleContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    Workspace workspace =
        authorizedGetWorkspace(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    workspaceDao.assertMcWorkspace(workspace, "deleteGoogleContext");
    jobService
        .newJob(
            "Delete Google Context " + workspaceId,
            UUID.randomUUID().toString(),
            DeleteGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .submitAndWait(null, false);
  }
}
