package bio.terra.workspace.service.workspace;

import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.exceptions.BufferServiceDisabledException;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.flight.CreateGoogleContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGoogleContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import io.opencensus.contrib.spring.aop.Traced;
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
  private final BufferServiceConfiguration bufferServiceConfiguration;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      SpendProfileService spendProfileService,
      BufferServiceConfiguration bufferServiceConfiguration) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
    this.bufferServiceConfiguration = bufferServiceConfiguration;
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

    return createJob.submitAndWait(UUID.class);
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
  public Workspace validateWorkspaceAndAction(
      AuthenticatedUserRequest userReq, UUID workspaceId, String action) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    samService.workspaceAuthzOnly(userReq, workspaceId, action);
    return workspace;
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    return validateWorkspaceAndAction(userReq, id, SamConstants.SAM_WORKSPACE_READ_ACTION);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    validateWorkspaceAndAction(userReq, id, SamConstants.SAM_WORKSPACE_DELETE_ACTION);

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

  /**
   * Start a job to create a Google cloud context for the workspace. Returns the job id. Verifies
   * workspace existence and write permission before starting the job.
   *
   * <p>This method is setup to dispatch by cloud type, but the only cloud type that makes it
   * through the controller validation is GCP.
   */
  @Traced
  public void createCloudContext(
      UUID workspaceId,
      CloudType cloudType,
      String jobId,
      String resultPath,
      AuthenticatedUserRequest userReq) {

    if (cloudType != CloudType.GCP) {
      throw new InternalLogicException("Should be unreachable");
    }

    if (!bufferServiceConfiguration.getEnabled()) {
      throw new BufferServiceDisabledException(
          "Cannot create a gcp context in an environment where buffer service is disabled or not configured.");
    }

    Workspace workspace =
        validateWorkspaceAndAction(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    assertMcWorkspace(workspace, "createCloudContext");

    // TODO: We should probably do this in a step of the job. It will be talking to another
    //  service and that may require retrying. It also may be slow, so getting it off of this
    //  thread and getting our response back might be better.
    SpendProfileId spendProfileId =
        workspace
            .getSpendProfileId()
            .orElseThrow(() -> new MissingSpendProfileException(workspaceId));
    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId, userReq);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw new NoBillingAccountException(spendProfileId);
    }

    jobService
        .newJob(
            "Create GCP Cloud Context " + workspaceId,
            jobId,
            CreateGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .addParameter(
            WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendProfile.billingAccountId().get())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  /**
   * Delete a cloud context for the workspace. Verifies workspace existence and write permission
   * before deleting the cloud context.
   */
  @Traced
  public void deleteCloudContext(
      UUID workspaceId, CloudType cloudType, AuthenticatedUserRequest userReq) {
    Workspace workspace =
        validateWorkspaceAndAction(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    assertMcWorkspace(workspace, "deleteGoogleContext");
    jobService
        .newJob(
            "Delete Google Context " + workspaceId,
            UUID.randomUUID().toString(),
            DeleteGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .submitAndWait(null);
  }

  public void assertMcWorkspace(UUID workspaceId, String actionMessage) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    assertMcWorkspace(workspace, actionMessage);
  }

  private void assertMcWorkspace(Workspace workspace, String actionMessage) {
    if (!WorkspaceStage.MC_WORKSPACE.equals(workspace.getWorkspaceStage())) {
      throw new StageDisabledException(
          workspace.getWorkspaceId(), workspace.getWorkspaceStage(), actionMessage);
    }
  }
}
