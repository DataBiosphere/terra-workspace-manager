package bio.terra.workspace.service.workspace;

import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneGcpWorkspaceFlight;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.exceptions.BufferServiceDisabledException;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2;
import bio.terra.workspace.service.workspace.flight.DeleteAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final AzureCloudContextService azureCloudContextService;
  private final GcpCloudContextService gcpCloudContextService;
  private final SamService samService;
  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final StageService stageService;
  private final FeatureConfiguration features;
  private final WorkspaceActivityLogDao workspaceActivityLogDao;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      BufferServiceConfiguration bufferServiceConfiguration,
      StageService stageService,
      AzureCloudContextService azureCloudContextService,
      GcpCloudContextService gcpCloudContextService,
      FeatureConfiguration features,
      WorkspaceActivityLogDao workspaceActivityLogDao) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.stageService = stageService;
    this.azureCloudContextService = azureCloudContextService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.features = features;
    this.workspaceActivityLogDao = workspaceActivityLogDao;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @Traced
  public UUID createWorkspace(Workspace workspace, AuthenticatedUserRequest userRequest) {
    String workspaceName = workspace.getDisplayName().orElse("");
    String workspaceUuid = workspace.getWorkspaceId().toString();
    String jobDescription =
        String.format("Create workspace: name: '%s' id: '%s'  ", workspaceName, workspaceUuid);

    JobBuilder createJob =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(WorkspaceCreateFlight.class)
            .request(workspace)
            .userRequest(userRequest)
            .workspaceId(workspaceUuid)
            .operationType(OperationType.CREATE)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name())
            .addParameter(WorkspaceFlightMapKeys.DISPLAY_NAME, workspaceName)
            .addParameter(
                WorkspaceFlightMapKeys.DESCRIPTION, workspace.getDescription().orElse(""));

    if (workspace.getSpendProfileId().isPresent()) {
      createJob.addParameter(
          WorkspaceFlightMapKeys.SPEND_PROFILE_ID, workspace.getSpendProfileId().get().getId());
    }
    // Skip the access check, which would fail since this workspace doesn't exist yet.
    return createJob.submitAndWait(UUID.class, false);
  }

  /**
   * Convenience function that checks existence of a workspace, followed by an authorization check
   * against that workspace.
   *
   * <p>Throws WorkspaceNotFoundException from getWorkspace if the workspace does not exist,
   * regardless of the user's permission.
   *
   * <p>Throws ForbiddenException if the user is not permitted to perform the specified action on
   * the workspace in question.
   *
   * <p>Returns the Workspace object if it exists and the user is permitted to perform the specified
   * action.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace in question
   * @param action the action to authorize against the workspace
   * @return the workspace, if it exists and the user is permitted to perform the specified action.
   */
  @Traced
  public Workspace validateWorkspaceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    logger.info(
        "validateWorkspaceAndAction - userRequest: {}\nworkspaceUuid: {}\naction: {}",
        userRequest,
        workspaceUuid,
        action);
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest, SamConstants.SamResource.WORKSPACE, workspaceUuid.toString(), action),
        "checkAuthz");
    return workspace;
  }

  /**
   * List all workspaces a user has read access to.
   *
   * @param userRequest Authentication object for the caller
   * @param offset The number of items to skip before starting to collect the result set.
   * @param limit The maximum number of items to return.
   */
  @Traced
  public List<Workspace> listWorkspaces(
      AuthenticatedUserRequest userRequest, int offset, int limit) {
    List<UUID> samWorkspaceIds =
        SamRethrow.onInterrupted(
            () -> samService.listWorkspaceIds(userRequest), "listWorkspaceIds");
    return workspaceDao.getWorkspacesMatchingList(samWorkspaceIds, offset, limit);
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID uuid, AuthenticatedUserRequest userRequest) {
    return validateWorkspaceAndAction(userRequest, uuid, SamConstants.SamWorkspaceAction.READ);
  }

  /** Retrieves an existing workspace by userFacingId */
  @Traced
  public Workspace getWorkspaceByUserFacingId(
      String userFacingId, AuthenticatedUserRequest userRequest) {
    logger.info(
        "getWorkspaceByUserFacingId - userRequest: {}\nuserFacingId: {}",
        userRequest,
        userFacingId);
    Workspace workspace = workspaceDao.getWorkspaceByUserFacingId(userFacingId);
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest,
                SamConstants.SamResource.WORKSPACE,
                workspace.getWorkspaceId().toString(),
                SamWorkspaceAction.READ),
        "checkAuthz");
    return workspace;
  }

  /**
   * Update an existing workspace. Currently, can change the workspace's display name or
   * description.
   *
   * @param userRequest authenticated user
   * @param workspaceUuid workspace of interest
   * @param name name to change - may be null
   * @param properties optional map of key-value properties
   * @param description description to change - may be null
   */
  public Workspace updateWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      @Nullable String userFacingId,
      @Nullable String name,
      @Nullable String description,
      @Nullable Map<String, String> properties) {
    validateWorkspaceAndAction(userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    if (workspaceDao.updateWorkspace(workspaceUuid, userFacingId, name, description, properties)) {
      workspaceActivityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.UPDATE));
    }
    return workspaceDao.getWorkspace(workspaceUuid);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(UUID uuid, AuthenticatedUserRequest userRequest) {
    Workspace workspace = validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.DELETE);
    String description = "Delete workspace " + uuid;
    JobBuilder deleteJob =
        jobService
            .newJob()
            .description(description)
            .flightClass(WorkspaceDeleteFlight.class)
            .operationType(OperationType.DELETE)
            .workspaceId(uuid.toString())
            .userRequest(userRequest)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
    // Skip access check, which can't succeed after the workspace is deleted
    deleteJob.submitAndWait(null, false);
  }

  /**
   * Process the request to create a Azure cloud context
   *
   * @param workspaceUuid workspace in which to create the context
   * @param jobId caller-supplied job id of the async job
   * @param userRequest user authentication info
   * @param resultPath optional endpoint where the result of the completed job can be retrieved
   * @param azureContext azure context information
   */
  @Traced
  public void createAzureCloudContext(
      UUID workspaceUuid,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath,
      AzureCloudContext azureContext) {
    features.azureEnabledCheck();

    Workspace workspace =
        validateWorkspaceAndAction(userRequest, workspaceUuid, SamWorkspaceAction.WRITE);
    stageService.assertMcWorkspace(workspace, "createCloudContext");

    jobService
        .newJob()
        .description("Create Azure Cloud Context " + workspaceUuid)
        .jobId(jobId)
        .operationType(OperationType.CREATE)
        .workspaceId(workspaceUuid.toString())
        .flightClass(CreateAzureContextFlight.class)
        .request(azureContext)
        .userRequest(userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  /**
   * Process the request to create a GCP cloud context
   *
   * @param workspaceUuid workspace in which to create the context
   * @param jobId caller-supplied job id of the async job
   * @param userRequest user authentication info
   * @param resultPath optional endpoint where the result of the completed job can be retrieved
   */
  @Traced
  public void createGcpCloudContext(
      UUID workspaceUuid,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath) {

    if (!bufferServiceConfiguration.getEnabled()) {
      throw new BufferServiceDisabledException(
          "Cannot create a GCP context in an environment where buffer service is disabled or not configured.");
    }

    Workspace workspace =
        validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    stageService.assertMcWorkspace(workspace, "createCloudContext");

    String workspaceName = workspace.getDisplayName().orElse("");
    String jobDescription =
        String.format(
            "Create GCP cloud context for workspace: name: '%s' id: '%s'  ",
            workspaceName, workspaceUuid);

    jobService
        .newJob()
        .description(jobDescription)
        .jobId(jobId)
        .flightClass(CreateGcpContextFlightV2.class)
        .userRequest(userRequest)
        .operationType(OperationType.CREATE)
        .workspaceId(workspaceUuid.toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  public void createGcpCloudContext(
      UUID workspaceUuid, String jobId, AuthenticatedUserRequest userRequest) {
    createGcpCloudContext(workspaceUuid, jobId, userRequest, null);
  }

  public String cloneWorkspace(
      UUID sourceWorkspaceId,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      Workspace destinationWorkspace) {
    final Workspace sourceWorkspace =
        validateWorkspaceAndAction(
            userRequest, sourceWorkspaceId, SamConstants.SamWorkspaceAction.READ);
    stageService.assertMcWorkspace(sourceWorkspace, "cloneGcpWorkspace");
    String workspaceName = sourceWorkspace.getDisplayName().orElse("");
    String workspaceUuid = sourceWorkspace.getWorkspaceId().toString();
    String jobDescription =
        String.format("Clone workspace: name: '%s' id: '%s'  ", workspaceName, workspaceUuid);

    // Create the destination workspace synchronously first.
    createWorkspace(destinationWorkspace, userRequest);

    // Remaining steps are an async flight.
    return jobService
        .newJob()
        .description(jobDescription)
        .flightClass(CloneGcpWorkspaceFlight.class)
        .userRequest(userRequest)
        .request(destinationWorkspace)
        .operationType(OperationType.CLONE)
        // allow UI to watch this job (and sub-flights) from dest workspace page during clone
        .workspaceId(destinationWorkspace.getWorkspaceId().toString())
        .addParameter(
            ControlledResourceKeys.SOURCE_WORKSPACE_ID,
            sourceWorkspaceId) // TODO: remove this duplication
        .addParameter(ControlledResourceKeys.LOCATION, location)
        .submit();
  }

  /**
   * Delete the GCP cloud context for the workspace. Verifies workspace existence and write
   * permission before deleting the cloud context.
   */
  @Traced
  public void deleteGcpCloudContext(UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    stageService.assertMcWorkspace(workspace, "deleteGcpCloudContext");
    String workspaceName = workspace.getDisplayName().orElse("");
    String jobDescription =
        String.format(
            "Delete GCP cloud context for workspace: name: '%s' id: '%s'  ",
            workspaceName, workspaceUuid);

    jobService
        .newJob()
        .description(jobDescription)
        .flightClass(DeleteGcpContextFlight.class)
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .workspaceId(workspaceUuid.toString())
        .submitAndWait(null);
  }

  public void deleteAzureCloudContext(UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(userRequest, workspaceUuid, SamWorkspaceAction.WRITE);

    stageService.assertMcWorkspace(workspace, "deleteAzureCloudContext");
    String workspaceName = workspace.getDisplayName().orElse("");
    String jobDescription =
        String.format(
            "Delete Azure cloud context for workspace: name: '%s' id: '%s'  ",
            workspaceName, workspaceUuid);
    jobService
        .newJob()
        .description(jobDescription)
        .flightClass(DeleteAzureContextFlight.class)
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .workspaceId(workspaceUuid.toString())
        .submitAndWait(null);
  }

  /**
   * We ensure that the workspace exists and the user has read access. If so, we lookup the Azure
   * cloud context, if any.
   *
   * @param workspaceUuid id of the workspace whose cloud context we want to get
   * @param userRequest auth of user to test for read access
   * @return optional Azure cloud context
   */
  public Optional<AzureCloudContext> getAuthorizedAzureCloudContext(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    validateWorkspaceAndAction(userRequest, workspaceUuid, SamWorkspaceAction.READ);
    features.azureEnabledCheck();
    return azureCloudContextService.getAzureCloudContext(workspaceUuid);
  }

  /**
   * We ensure that the workspace exists and the user has read access. If so, we lookup the GCP
   * project id, if any.
   *
   * @param workspaceUuid id of the workspace whose GCP project id we want to get
   * @param userRequest auth of user to test for read access
   * @return optional project id
   */
  public Optional<String> getAuthorizedGcpProject(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    validateWorkspaceAndAction(userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return gcpCloudContextService.getGcpProject(workspaceUuid);
  }

  /**
   * We ensure that the workspace exists and the user has read access. If so, we lookup the GCP
   * project id. If it does not exist, an exception is thrown.
   *
   * @param workspaceUuid id of the workspace whose GCP project id we want to get
   * @param userRequest auth of user to test for read access
   * @return project id
   */
  public String getAuthorizedRequiredGcpProject(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    validateWorkspaceAndAction(userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
  }

  /**
   * Remove a workspace role from a user. This will also remove a user from their private resources
   * if they are no longer a member of the workspace (i.e. have no other roles) after role removal.
   *
   * @param workspaceUuid ID of the workspace user to remove user's role in
   * @param role Role to remove
   * @param rawUserEmail Email identifier of user whose role is being removed
   * @param executingUserRequest User credentials to authenticate this removal. Must belong to a
   *     workspace owner, and likely do not belong to {@code userEmail}.
   */
  public void removeWorkspaceRoleFromUser(
      UUID workspaceUuid,
      WsmIamRole role,
      String rawUserEmail,
      AuthenticatedUserRequest executingUserRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(
            executingUserRequest, workspaceUuid, SamConstants.SamWorkspaceAction.OWN);
    stageService.assertMcWorkspace(workspace, "removeWorkspaceRoleFromUser");
    // GCP always uses lowercase email identifiers, so we do the same here.
    String targetUserEmail = rawUserEmail.toLowerCase();
    // Before launching the flight, validate that the user being removed is a direct member of the
    // specified role. Users may also be added to a workspace via managed groups, but WSM does not
    // control membership of those groups, and so cannot remove them here.
    List<String> roleMembers =
        samService.listUsersWithWorkspaceRole(workspaceUuid, role, executingUserRequest).stream()
            // SAM does not always use lowercase emails, so lowercase everything here before the
            // contains check below
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    if (!roleMembers.contains(targetUserEmail)) {
      return;
    }
    jobService
        .newJob()
        .description(
            String.format(
                "Remove role %s from user %s in workspace %s",
                role.name(), targetUserEmail, workspaceUuid))
        .flightClass(RemoveUserFromWorkspaceFlight.class)
        .userRequest(executingUserRequest)
        .operationType(OperationType.REMOVE_WORKSPACE_ROLE)
        .workspaceId(workspaceUuid.toString())
        .addParameter(WorkspaceFlightMapKeys.USER_TO_REMOVE, targetUserEmail)
        .addParameter(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, role)
        .submitAndWait(null);
  }
}
