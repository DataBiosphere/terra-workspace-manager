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
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.exceptions.BufferServiceDisabledException;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Service for workspace lifecycle operations.
 *
 * <p>This service holds core workspace management operations like creating, reading, and deleting
 * workspaces as well as their cloud contexts. New methods generally should go in new services.
 */
@Lazy
@Component
public class WorkspaceService {

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final SpendProfileService spendProfileService;
  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final StageService stageService;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      SpendProfileService spendProfileService,
      BufferServiceConfiguration bufferServiceConfiguration,
      StageService stageService) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.stageService = stageService;
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

    createJob.addParameter(
        WorkspaceFlightMapKeys.DISPLAY_NAME_ID, workspaceRequest.displayName().orElse(""));
    createJob.addParameter(
        WorkspaceFlightMapKeys.DESCRIPTION_ID, workspaceRequest.description().orElse(""));
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

  /**
   * List all workspaces a user has read access to.
   *
   * @param userReq Authentication object for the caller
   * @param offset The number of items to skip before starting to collect the result set.
   * @param limit The maximum number of items to return.
   */
  @Traced
  public List<Workspace> listWorkspaces(AuthenticatedUserRequest userReq, int offset, int limit) {
    List<UUID> samWorkspaceIds = samService.listWorkspaceIds(userReq);
    return workspaceDao.getWorkspacesMatchingList(samWorkspaceIds, offset, limit);
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    return validateWorkspaceAndAction(userReq, id, SamConstants.SAM_WORKSPACE_READ_ACTION);
  }

  /**
   * Update an existing workspace. Currently, can change the workspace's display name or
   * description.
   *
   * @param workspaceId workspace of interest
   * @param name name to change - may be null
   * @param description description to change - may be null
   */
  public Workspace updateWorkspace(
      AuthenticatedUserRequest userReq,
      UUID workspaceId,
      @Nullable String name,
      @Nullable String description) {
    validateWorkspaceAndAction(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    workspaceDao.updateWorkspace(workspaceId, name, description);
    return workspaceDao.getWorkspace(workspaceId);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    Workspace workspace =
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
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, id)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage());
    deleteJob.submitAndWait(null);
  }

  /**
   * Process the request to create a GCP cloud context
   *
   * @param workspaceId workspace in which to create the context
   * @param jobId called-supplied job id of the async job
   * @param resultPath endpoint where the result of the completed job can be retrieved
   * @param userReq user authentication info
   */
  @Traced
  public void createGcpCloudContext(
      UUID workspaceId, String jobId, String resultPath, AuthenticatedUserRequest userReq) {

    if (!bufferServiceConfiguration.getEnabled()) {
      throw new BufferServiceDisabledException(
          "Cannot create a GCP context in an environment where buffer service is disabled or not configured.");
    }

    Workspace workspace =
        validateWorkspaceAndAction(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    stageService.assertMcWorkspace(workspace, "createCloudContext");

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
            CreateGcpContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .addParameter(
            WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendProfile.billingAccountId().get())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  /**
   * Delete the GCP cloud context for the workspace. Verifies workspace existence and write
   * permission before deleting the cloud context.
   */
  @Traced
  public void deleteGcpCloudContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    Workspace workspace =
        validateWorkspaceAndAction(userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    stageService.assertMcWorkspace(workspace, "deleteGcpCloudContext");
    jobService
        .newJob(
            "Delete GCP Context " + workspaceId,
            UUID.randomUUID().toString(),
            DeleteGcpContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .submitAndWait(null);
  }

  /**
   * Helper method used by other classes that require the GCP project to exist in the workspace. It
   * throws if the project (GCP cloud context) is not set up.
   *
   * @param workspaceId unique workspace id
   * @return GCP project id
   */
  public String getRequiredGcpProject(UUID workspaceId) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    GcpCloudContext gcpCloudContext =
        workspace
            .getGcpCloudContext()
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires GCP cloud context"));
    return gcpCloudContext.getGcpProjectId();
  }
}
