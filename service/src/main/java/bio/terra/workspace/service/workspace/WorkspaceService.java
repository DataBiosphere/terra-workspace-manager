package bio.terra.workspace.service.workspace;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneWorkspaceFlight;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.exceptions.BufferServiceDisabledException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2;
import bio.terra.workspace.service.workspace.flight.DeleteAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceAndHighestRole;
import com.google.common.base.Preconditions;
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
  private final ApplicationDao applicationDao;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final StageService stageService;
  private final FeatureConfiguration features;
  private final TpsApiDispatch tpsApiDispatch;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      ApplicationDao applicationDao,
      WorkspaceDao workspaceDao,
      SamService samService,
      BufferServiceConfiguration bufferServiceConfiguration,
      StageService stageService,
      FeatureConfiguration features,
      TpsApiDispatch tpsApiDispatch,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.jobService = jobService;
    this.applicationDao = applicationDao;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.stageService = stageService;
    this.features = features;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @Traced
  public UUID createWorkspace(
      Workspace workspace,
      @Nullable TpsPolicyInputs policies,
      @Nullable List<String> applications,
      AuthenticatedUserRequest userRequest) {
    String workspaceUuid = workspace.getWorkspaceId().toString();
    String jobDescription =
        String.format(
            "Create workspace: name: '%s' id: '%s'  ",
            workspace.getDisplayName().orElse(""), workspaceUuid);

    // Before launching the flight, confirm the workspace does not already exist. This isn't perfect
    // if two requests come in at nearly the same time, but it prevents launching a flight when a
    // workspace already exists.
    if (workspaceDao.getWorkspaceIfExists(workspace.getWorkspaceId()).isPresent()) {
      throw new DuplicateWorkspaceException("Provided workspace ID is already in use");
    }

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
            .addParameter(WorkspaceFlightMapKeys.POLICIES, policies)
            .addParameter(WorkspaceFlightMapKeys.APPLICATION_IDS, applications);

    if (workspace.getSpendProfileId().isPresent()) {
      createJob.addParameter(
          WorkspaceFlightMapKeys.SPEND_PROFILE_ID, workspace.getSpendProfileId().get().getId());
    }
    return createJob.submitAndWait(UUID.class);
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
   * Wrapper around {@link #validateWorkspaceAndAction(AuthenticatedUserRequest, UUID, String)}
   * which additionally throws StageDisabledException if this is not an MC_WORKSPACE stage
   * workspace.
   */
  @Traced
  public Workspace validateMcWorkspaceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    Workspace workspace = validateWorkspaceAndAction(userRequest, workspaceUuid, action);
    stageService.assertMcWorkspace(workspace, action);
    return workspace;
  }

  /**
   * A special case of {@code validateWorkspaceAndAction} for clone operations.
   *
   * <p>Unlike most other operations, cloning requires two authz checks: read access to the source
   * object, plus write access to the destination object.
   *
   * <p>This method is only for authz checks on referenced resource clones, which do not have their
   * own Sam object representation. To check auth for a controlled resource clone, use {@link
   * bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager#validateCloneAction(AuthenticatedUserRequest,
   * UUID, UUID, UUID)}
   *
   * @return The source Workspace object.
   */
  @Traced
  public Workspace validateCloneReferenceAction(
      AuthenticatedUserRequest userRequest, UUID sourceWorkspaceId, UUID destinationWorkspaceId) {
    Workspace sourceWorkspace =
        validateWorkspaceAndAction(userRequest, sourceWorkspaceId, SamWorkspaceAction.READ);
    validateWorkspaceAndAction(
        userRequest, destinationWorkspaceId, SamWorkspaceAction.CREATE_REFERENCE);
    return sourceWorkspace;
  }

  /**
   * List all workspaces a user has read access to.
   *
   * @param userRequest Authentication object for the caller
   * @param offset The number of items to skip before starting to collect the result set.
   * @param limit The maximum number of items to return.
   */
  @Traced
  public List<WorkspaceAndHighestRole> listWorkspacesAndHighestRoles(
      AuthenticatedUserRequest userRequest, int offset, int limit, WsmIamRole minimumHighestRole) {
    // In general, highest SAM role should be fetched in controller. Fetch here to save a SAM call.
    Map<UUID, WsmIamRole> samWorkspaceIdsAndHighestRoles =
        SamRethrow.onInterrupted(
            () -> samService.listWorkspaceIdsAndHighestRoles(userRequest, minimumHighestRole),
            "listWorkspaceIds");
    return workspaceDao
        .getWorkspacesMatchingList(samWorkspaceIdsAndHighestRoles.keySet(), offset, limit)
        .stream()
        .map(
            workspace -> {
              WsmIamRole highestRole =
                  samWorkspaceIdsAndHighestRoles.get(workspace.getWorkspaceId());
              Workspace workspaceToReturn =
                  highestRole == WsmIamRole.DISCOVERER
                      ? Workspace.stripWorkspaceForRequesterWithOnlyDiscovererRole(workspace)
                      : workspace;
              return new WorkspaceAndHighestRole(workspaceToReturn, highestRole);
            })
        .toList();
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID uuid) {
    return workspaceDao.getWorkspace(uuid);
  }

  /** Retrieves an existing workspace by userFacingId */
  @Traced
  public Workspace getWorkspaceByUserFacingId(
      String userFacingId,
      AuthenticatedUserRequest userRequest,
      WsmIamRole minimumHighestRoleFromRequest) {
    logger.info(
        "getWorkspaceByUserFacingId - userRequest: {}\nuserFacingId: {}",
        userRequest,
        userFacingId);
    Workspace workspace = workspaceDao.getWorkspaceByUserFacingId(userFacingId);
    // This is one exception where we need to do an authz check inside a service instead of a
    // controller. This is because checks with Sam require the workspace ID, but until we read from
    // WSM's database we only have the user-facing ID.
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest,
                SamConstants.SamResource.WORKSPACE,
                workspace.getWorkspaceId().toString(),
                minimumHighestRoleFromRequest.toSamAction()),
        "checkAuthz");
    return workspace;
  }

  @Traced
  public WsmIamRole getHighestRole(UUID uuid, AuthenticatedUserRequest userRequest) {
    logger.info("getHighestRole - userRequest: {}\nuserFacingId: {}", userRequest, uuid.toString());
    List<WsmIamRole> requesterRoles =
        SamRethrow.onInterrupted(
            () ->
                samService.listRequesterRoles(
                    userRequest, SamConstants.SamResource.WORKSPACE, uuid.toString()),
            "listRequesterRoles");
    Optional<WsmIamRole> highestRole = WsmIamRole.getHighestRole(uuid, requesterRoles);
    Preconditions.checkState(
        highestRole.isPresent(), String.format("Workspace %s missing roles", uuid.toString()));
    return highestRole.get();
  }

  /**
   * Update an existing workspace. Currently, can change the workspace's display name or
   * description.
   *
   * @param workspaceUuid workspace of interest
   * @param name name to change - may be null
   * @param description description to change - may be null
   */
  public Workspace updateWorkspace(
      UUID workspaceUuid,
      @Nullable String userFacingId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userRequest) {
    if (workspaceDao.updateWorkspace(workspaceUuid, userFacingId, name, description)) {
      workspaceActivityLogService.writeActivity(
          userRequest,
          workspaceUuid,
          OperationType.UPDATE,
          workspaceUuid.toString(),
          ActivityLogChangedTarget.WORKSPACE);
    }
    return workspaceDao.getWorkspace(workspaceUuid);
  }

  /**
   * Update an existing workspace properties.
   *
   * @param workspaceUuid workspace of interest
   * @param properties list of keys in properties
   */
  public void updateWorkspaceProperties(
      UUID workspaceUuid, Map<String, String> properties, AuthenticatedUserRequest userRequest) {
    workspaceDao.updateWorkspaceProperties(workspaceUuid, properties);
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.UPDATE_PROPERTIES,
        workspaceUuid.toString(),
        ActivityLogChangedTarget.WORKSPACE);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(Workspace workspace, AuthenticatedUserRequest userRequest) {
    String description = "Delete workspace " + workspace.getWorkspaceId();
    JobBuilder deleteJob =
        jobService
            .newJob()
            .description(description)
            .flightClass(WorkspaceDeleteFlight.class)
            .operationType(OperationType.DELETE)
            .workspaceId(workspace.getWorkspaceId().toString())
            .userRequest(userRequest)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
    deleteJob.submitAndWait(null);
  }

  /**
   * Update an existing workspace properties.
   *
   * @param workspaceUuid workspace of interest
   * @param propertyKeys list of keys in properties
   */
  public void deleteWorkspaceProperties(
      UUID workspaceUuid, List<String> propertyKeys, AuthenticatedUserRequest userRequest) {
    workspaceDao.deleteWorkspaceProperties(workspaceUuid, propertyKeys);
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.DELETE_PROPERTIES,
        workspaceUuid.toString(),
        ActivityLogChangedTarget.WORKSPACE);
  }

  /**
   * Process the request to create a Azure cloud context
   *
   * @param workspace workspace in which to create the context
   * @param jobId caller-supplied job id of the async job
   * @param userRequest user authentication info
   * @param resultPath optional endpoint where the result of the completed job can be retrieved
   */
  @Traced
  public void createAzureCloudContext(
      Workspace workspace,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath) {
    features.azureEnabledCheck();

    jobService
        .newJob()
        .description("Create Azure Cloud Context " + workspace.getWorkspaceId())
        .jobId(jobId)
        .workspaceId(workspace.getWorkspaceId().toString())
        .operationType(OperationType.CREATE)
        .flightClass(CreateAzureContextFlight.class)
        .userRequest(userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspace.getWorkspaceId().toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  /**
   * Process the request to create a GCP cloud context
   *
   * @param workspace workspace in which to create the context
   * @param jobId caller-supplied job id of the async job
   * @param userRequest user authentication info
   * @param resultPath optional endpoint where the result of the completed job can be retrieved
   */
  @Traced
  public void createGcpCloudContext(
      Workspace workspace,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath) {

    if (!bufferServiceConfiguration.getEnabled()) {
      throw new BufferServiceDisabledException(
          "Cannot create a GCP context in an environment where buffer service is disabled or not configured.");
    }

    String jobDescription =
        String.format(
            "Create GCP cloud context for workspace: name: '%s' id: '%s'  ",
            workspace.getDisplayName().orElse(""), workspace.getWorkspaceId());

    jobService
        .newJob()
        .description(jobDescription)
        .jobId(jobId)
        .flightClass(CreateGcpContextFlightV2.class)
        .userRequest(userRequest)
        .operationType(OperationType.CREATE)
        .workspaceId(workspace.getWorkspaceId().toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  public void createGcpCloudContext(
      Workspace workspace, String jobId, AuthenticatedUserRequest userRequest) {
    createGcpCloudContext(workspace, jobId, userRequest, null);
  }

  public String cloneWorkspace(
      Workspace sourceWorkspace,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      Workspace destinationWorkspace) {
    String workspaceUuid = sourceWorkspace.getWorkspaceId().toString();
    String jobDescription =
        String.format(
            "Clone workspace: name: '%s' id: '%s'  ",
            sourceWorkspace.getDisplayName().orElse(""), workspaceUuid);

    // Get the enabled applications from the source workspace
    List<String> applicationIds =
        applicationDao.listWorkspaceApplicationsForClone(sourceWorkspace.getWorkspaceId());

    // Create the destination workspace synchronously first.
    createWorkspace(destinationWorkspace, null, applicationIds, userRequest);

    // Remaining steps are an async flight.
    return jobService
        .newJob()
        .description(jobDescription)
        .flightClass(CloneWorkspaceFlight.class)
        .userRequest(userRequest)
        .request(destinationWorkspace)
        .operationType(OperationType.CLONE)
        // allow UI to watch this job (and sub-flights) from dest workspace page during clone
        .workspaceId(destinationWorkspace.getWorkspaceId().toString())
        .addParameter(
            ControlledResourceKeys.SOURCE_WORKSPACE_ID,
            sourceWorkspace.getWorkspaceId()) // TODO: remove this duplication
        .addParameter(ControlledResourceKeys.LOCATION, location)
        .submit();
  }

  /** Delete the GCP cloud context for the workspace. */
  @Traced
  public void deleteGcpCloudContext(Workspace workspace, AuthenticatedUserRequest userRequest) {
    String jobDescription =
        String.format(
            "Delete GCP cloud context for workspace: name: '%s' id: '%s'  ",
            workspace.getDisplayName().orElse(""), workspace.getWorkspaceId());

    jobService
        .newJob()
        .description(jobDescription)
        .flightClass(DeleteGcpContextFlight.class)
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .workspaceId(workspace.getWorkspaceId().toString())
        .submitAndWait(null);
  }

  public void deleteAzureCloudContext(Workspace workspace, AuthenticatedUserRequest userRequest) {
    String jobDescription =
        String.format(
            "Delete Azure cloud context for workspace: name: '%s' id: '%s'  ",
            workspace.getDisplayName().orElse(""), workspace.getWorkspaceId());
    jobService
        .newJob()
        .description(jobDescription)
        .flightClass(DeleteAzureContextFlight.class)
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .workspaceId(workspace.getWorkspaceId().toString())
        .submitAndWait(null);
  }

  /**
   * Remove a workspace role from a user. This will also remove a user from their private resources
   * if they are no longer a member of the workspace (i.e. have no other roles) after role removal.
   *
   * @param workspace Workspace to remove user's role from
   * @param role Role to remove
   * @param rawUserEmail Email identifier of user whose role is being removed
   * @param executingUserRequest User credentials to authenticate this removal. Must belong to a
   *     workspace owner, and likely do not belong to {@code userEmail}.
   */
  public void removeWorkspaceRoleFromUser(
      Workspace workspace,
      WsmIamRole role,
      String rawUserEmail,
      AuthenticatedUserRequest executingUserRequest) {
    // GCP always uses lowercase email identifiers, so we do the same here.
    String targetUserEmail = rawUserEmail.toLowerCase();
    // Before launching the flight, validate that the user being removed is a direct member of the
    // specified role. Users may also be added to a workspace via managed groups, but WSM does not
    // control membership of those groups, and so cannot remove them here.
    List<String> roleMembers =
        samService
            .listUsersWithWorkspaceRole(workspace.getWorkspaceId(), role, executingUserRequest)
            .stream()
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
                role.name(), targetUserEmail, workspace.getWorkspaceId()))
        .flightClass(RemoveUserFromWorkspaceFlight.class)
        .userRequest(executingUserRequest)
        .workspaceId(workspace.getWorkspaceId().toString())
        .operationType(OperationType.REMOVE_WORKSPACE_ROLE)
        .addParameter(WorkspaceFlightMapKeys.USER_TO_REMOVE, targetUserEmail)
        .addParameter(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, role.name())
        .submitAndWait(null);
  }
}
