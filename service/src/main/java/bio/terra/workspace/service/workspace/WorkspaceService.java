package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLOUD_PLATFORM;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.SPEND_PROFILE;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.db.model.DbWorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.AccessibleWorkspace;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneWorkspaceFlight;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.InvalidCloudContextStateException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.create.workspace.CreateWorkspaceV2Flight;
import bio.terra.workspace.service.workspace.flight.create.workspace.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.workspace.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceDescription;
import com.google.common.base.Preconditions;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  private final StageService stageService;
  private final ResourceDao resourceDao;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  private final TpsApiDispatch tpsApiDispatch;
  private final PolicyValidator policyValidator;
  private final FeatureConfiguration features;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      ApplicationDao applicationDao,
      WorkspaceDao workspaceDao,
      SamService samService,
      StageService stageService,
      ResourceDao resourceDao,
      WorkspaceActivityLogService workspaceActivityLogService,
      TpsApiDispatch tpsApiDispatch,
      PolicyValidator policyValidator,
      FeatureConfiguration features) {
    this.jobService = jobService;
    this.applicationDao = applicationDao;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.stageService = stageService;
    this.resourceDao = resourceDao;
    this.workspaceActivityLogService = workspaceActivityLogService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.policyValidator = policyValidator;
    this.features = features;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @WithSpan
  public UUID createWorkspace(
      Workspace workspace,
      @Nullable TpsPolicyInputs policies,
      @Nullable List<String> applications,
      @Nullable String projectOwnerGroupId,
      AuthenticatedUserRequest userRequest) {
    return createWorkspaceWorker(
        workspace,
        policies,
        applications,
        /*sourceWorkspaceUuid=*/ null,
        CloningInstructions.COPY_NOTHING,
        projectOwnerGroupId,
        userRequest);
  }

  @WithSpan
  public UUID createWorkspaceForClone(
      Workspace workspace,
      @Nullable TpsPolicyInputs policies,
      @Nullable List<String> applications,
      @Nullable String projectOwnerGroupId,
      UUID sourceWorkspaceId,
      CloningInstructions cloningInstructions,
      AuthenticatedUserRequest userRequest) {
    return createWorkspaceWorker(
        workspace,
        policies,
        applications,
        sourceWorkspaceId,
        cloningInstructions,
        projectOwnerGroupId,
        userRequest);
  }

  @WithSpan
  public void createWorkspaceV2(
      Workspace workspace,
      @Nullable TpsPolicyInputs policies,
      @Nullable List<String> applications,
      @Nullable CloudPlatform cloudPlatform,
      @Nullable SpendProfile spendProfile,
      @Nullable String projectOwnerGroupId,
      String jobId,
      AuthenticatedUserRequest userRequest) {

    UUID workspaceUuid = workspace.getWorkspaceId();
    JobBuilder createJob =
        jobService
            .newJob()
            .jobId(jobId)
            .description(
                String.format(
                    "Create workspace name: '%s' id: '%s' and %s cloud context",
                    workspace.getDisplayName().orElse(""),
                    workspaceUuid,
                    (cloudPlatform == null ? "no" : cloudPlatform.toSql())))
            .flightClass(CreateWorkspaceV2Flight.class)
            .request(workspace)
            .userRequest(userRequest)
            .workspaceId(workspaceUuid.toString())
            .operationType(OperationType.CREATE)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name())
            .addParameter(WorkspaceFlightMapKeys.POLICIES, policies)
            .addParameter(WorkspaceFlightMapKeys.APPLICATION_IDS, applications)
            .addParameter(WorkspaceFlightMapKeys.PROJECT_OWNER_GROUP_ID, projectOwnerGroupId);

    // Add the cloud context params if we are making a cloud context
    // We mint a flight id here, so it is reliably constant for the inner cloud context flight
    if (cloudPlatform != null) {
      createJob
          .addParameter(CLOUD_PLATFORM, cloudPlatform)
          .addParameter(SPEND_PROFILE, spendProfile)
          .addParameter(
              WorkspaceFlightMapKeys.CREATE_CLOUD_CONTEXT_FLIGHT_ID, UUID.randomUUID().toString());
    }

    createJob.submit();

    // Wait for the metadata row to show up or the flight to fail
    jobService.waitForMetadataOrJob(
        jobId, () -> Optional.ofNullable(workspaceDao.getDbWorkspace(workspaceUuid)).isPresent());
  }

  /**
   * Shared method for creating a workspace. It handles both the standalone create workspace and the
   * create-for-clone. When we create for clone, we have a source workspace and we merge or link
   * based on the source workspace.
   *
   * @param workspace object describing the workspace to create
   * @param policies nullable initial policies to set on the workspace
   * @param applications nullable applications to enable
   * @param sourceWorkspaceUuid nullable source workspace id if doing create-for-clone
   * @param cloningInstructions COPY_NOTHING for a new create; COPY_REFERENCE or LINK_REFERENCE for
   *     a clone
   * @param projectOwnerGroupId nullable Sam resource group ID which allows the group to be added as
   *     project owner on the workspace
   * @param userRequest identity of the creator
   * @return id of the new workspace
   */
  private UUID createWorkspaceWorker(
      Workspace workspace,
      @Nullable TpsPolicyInputs policies,
      @Nullable List<String> applications,
      @Nullable UUID sourceWorkspaceUuid,
      CloningInstructions cloningInstructions,
      @Nullable String projectOwnerGroupId,
      AuthenticatedUserRequest userRequest) {

    String workspaceUuid = workspace.getWorkspaceId().toString();
    JobBuilder createJob =
        jobService
            .newJob()
            .description(
                String.format(
                    "Create workspace: name: '%s' id: '%s'  ",
                    workspace.getDisplayName().orElse(""), workspaceUuid))
            .flightClass(WorkspaceCreateFlight.class)
            .request(workspace)
            .userRequest(userRequest)
            .workspaceId(workspaceUuid)
            .operationType(OperationType.CREATE)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name())
            .addParameter(WorkspaceFlightMapKeys.POLICIES, policies)
            .addParameter(WorkspaceFlightMapKeys.APPLICATION_IDS, applications)
            .addParameter(
                WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS, cloningInstructions)
            .addParameter(SOURCE_WORKSPACE_ID, sourceWorkspaceUuid)
            .addParameter(WorkspaceFlightMapKeys.PROJECT_OWNER_GROUP_ID, projectOwnerGroupId);

    workspace
        .getSpendProfileId()
        .ifPresent(
            spendProfileId ->
                createJob.addParameter(
                    WorkspaceFlightMapKeys.SPEND_PROFILE_ID, spendProfileId.getId()));
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
  @WithSpan
  public Workspace validateWorkspaceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    logWorkspaceAction(userRequest, workspaceUuid.toString(), action);
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
    checkWorkspaceAuthz(userRequest, workspaceUuid, action);
    return workspace;
  }

  /**
   * Like validateWorkspaceAndAction, but returns the full workspace description
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace in question
   * @param action the action to authorize against the workspace
   * @return the workspace description
   */
  @WithSpan
  public WorkspaceDescription validateWorkspaceAndActionReturningDescription(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    logWorkspaceAction(userRequest, workspaceUuid.toString(), action);
    DbWorkspaceDescription dbWorkspaceDescription =
        workspaceDao.getWorkspaceDescription(workspaceUuid);
    checkWorkspaceAuthz(userRequest, workspaceUuid, action);
    return makeWorkspaceDescription(userRequest, dbWorkspaceDescription);
  }

  /**
   * Like validateWorkspaceAndAction, but finds the workspace by user facing id.
   *
   * @param userRequest the user's authenticated request
   * @param userFacingId user facing id of the workspace in question
   * @param action the action to authorize against the workspace
   * @return the workspace description
   */
  @WithSpan
  public WorkspaceDescription validateWorkspaceAndActionReturningDescription(
      AuthenticatedUserRequest userRequest, String userFacingId, String action) {
    logWorkspaceAction(userRequest, userFacingId, action);
    DbWorkspaceDescription dbWorkspaceDescription =
        workspaceDao.getWorkspaceDescriptionByUserFacingId(userFacingId);
    checkWorkspaceAuthz(userRequest, dbWorkspaceDescription.getWorkspace().workspaceId(), action);
    return makeWorkspaceDescription(userRequest, dbWorkspaceDescription);
  }

  /**
   * Wrapper around {@link #validateWorkspaceAndAction(AuthenticatedUserRequest, UUID, String)}
   * which additionally throws StageDisabledException if this is not an MC_WORKSPACE stage
   * workspace.
   */
  @WithSpan
  public Workspace validateMcWorkspaceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    Workspace workspace = validateWorkspaceAndAction(userRequest, workspaceUuid, action);
    stageService.assertMcWorkspace(workspace, action);
    return workspace;
  }

  // -- private methods supporting validateWorkspaceAndAction methods --
  private void logWorkspaceAction(
      AuthenticatedUserRequest userRequest, String idString, String action) {
    logger.info(
        "validateWorkspaceAndAction - userRequest: {}\nworkspace UUID/UFID: {}\naction: {}",
        userRequest,
        idString,
        action);
  }

  private void checkWorkspaceAuthz(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, String action) {
    Rethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest, SamConstants.SamResource.WORKSPACE, workspaceUuid.toString(), action),
        "checkAuthz");
  }

  private WorkspaceDescription makeWorkspaceDescription(
      AuthenticatedUserRequest userRequest, DbWorkspaceDescription dbWorkspaceDescription) {
    TpsPaoGetResult workspacePao = null;
    if (features.isTpsEnabled()) {
      workspacePao =
          Rethrow.onInterrupted(
              () ->
                  tpsApiDispatch.getOrCreatePao(
                      dbWorkspaceDescription.getWorkspace().workspaceId(),
                      TpsComponent.WSM,
                      TpsObjectType.WORKSPACE),
              "getOrCreatePao");
    }
    return new WorkspaceDescription(
        dbWorkspaceDescription.getWorkspace(),
        getHighestRole(dbWorkspaceDescription.getWorkspace().workspaceId(), userRequest),
        /* missingAuthDomainGroups= */ null,
        dbWorkspaceDescription.getLastUpdatedByEmail(),
        dbWorkspaceDescription.getLastUpdatedByDate(),
        dbWorkspaceDescription.getAwsCloudContext(),
        dbWorkspaceDescription.getAzureCloudContext(),
        dbWorkspaceDescription.getGcpCloudContext(),
        workspacePao);
  }

  @WithSpan
  public void validateWorkspaceState(UUID workspaceUuid) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
    validateWorkspaceState(workspace);
  }

  /**
   * Test that the workspace is in a ready state. This is designed to be used right after
   * validateWorkspaceAndAction
   *
   * @param workspace object describing the workspace, including its current state
   */
  public void validateWorkspaceState(Workspace workspace) {
    if (workspace.state() != WsmResourceState.READY) {
      throw new InvalidCloudContextStateException(
          String.format(
              "Workspace is busy %s. Try again later.", workspace.state().toApi().toString()));
    }
  }

  /**
   * Variant of validation that takes a workspace uuid, retrieves the workspace, and then does the
   * regular validation. Designed for use in resource operations that do not test workspace
   * permissions.
   *
   * @param workspaceUuid id of the workspace to validate
   * @param cloudPlatform cloud platform for cloud context to validate
   * @return cloud context object for the platform
   */
  public CloudContext validateWorkspaceAndContextState(
      UUID workspaceUuid, CloudPlatform cloudPlatform) {
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
    return validateWorkspaceAndContextState(workspace, cloudPlatform);
  }

  /**
   * Test that the workspace and cloud context are in a ready state. This is designed to be used
   * right after validateWorkspaceAndAction for cases where we want to validate that the workspace
   * and cloud context are in the READY state.
   *
   * <p>NOTE: this is not perfect concurrency control. We could add share locks on the workspace and
   * cloud context to achieve that. We've decided not to add that complexity. We expect this check
   * to cover the practical usage.
   *
   * @param workspace object describing the workspace, including its current state
   * @param cloudPlatform cloud platform for cloud context to validate
   * @return cloud context object for the platform
   */
  public CloudContext validateWorkspaceAndContextState(
      Workspace workspace, CloudPlatform cloudPlatform) {
    validateWorkspaceState(workspace);
    Optional<DbCloudContext> optionalDbCloudContext =
        workspaceDao.getCloudContext(workspace.workspaceId(), cloudPlatform);
    if (optionalDbCloudContext.isEmpty()) {
      throw new CloudContextRequiredException(
          String.format(
              "Operation requires %s cloud context", cloudPlatform.toApiModel().toString()));
    }
    if (optionalDbCloudContext.get().getState() != WsmResourceState.READY) {
      throw new InvalidCloudContextStateException(
          String.format(
              "%s cloud context is busy %s. Try again later.",
              cloudPlatform.toApiModel().toString(), workspace.state().toApi().toString()));
    }

    return switch (cloudPlatform) {
      case AWS -> AwsCloudContext.deserialize(optionalDbCloudContext.get());
      case AZURE -> AzureCloudContext.deserialize(optionalDbCloudContext.get());
      case GCP -> GcpCloudContext.deserialize(optionalDbCloudContext.get());
      default -> throw new InternalLogicException("Invalid cloud platform " + cloudPlatform);
    };
  }

  /**
   * Validate the workspace state and possibly the cloud context of the target of a clone. If the
   * cloud platform is ANY or we are not creating a new controlled resource, then we do not require
   * a target cloud context.
   *
   * @param workspaceUuid target workspace
   * @param cloudPlatform cloud platform
   * @param cloningInstructions cloniing instructions
   */
  public void validateCloneWorkspaceAndContextState(
      UUID workspaceUuid, CloudPlatform cloudPlatform, CloningInstructions cloningInstructions) {
    // If we do not require a target cloud context, just check the workspace
    if (cloudPlatform == CloudPlatform.ANY
        || (cloningInstructions != CloningInstructions.COPY_DEFINITION
            && cloningInstructions != CloningInstructions.COPY_RESOURCE)) {
      validateWorkspaceState(workspaceUuid);
    } else {
      validateWorkspaceAndContextState(workspaceUuid, cloudPlatform);
    }
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
  @WithSpan
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
  @WithSpan
  public List<WorkspaceDescription> getWorkspaceDescriptions(
      AuthenticatedUserRequest userRequest, int offset, int limit, WsmIamRole minimumHighestRole) {

    // From Sam, retrieve the workspace id, highest role, and missing auth domain groups for all
    // workspaces the user has access to.
    Map<UUID, AccessibleWorkspace> accessibleWorkspaces =
        Rethrow.onInterrupted(
            () -> samService.listWorkspaceIdsAndHighestRoles(userRequest, minimumHighestRole),
            "listWorkspaceIds");

    // From DAO, retrieve the workspace metadata
    Map<UUID, DbWorkspaceDescription> dbWorkspaceDescriptions =
        workspaceDao.getWorkspaceDescriptionMapFromIdList(
            accessibleWorkspaces.keySet(), offset, limit);

    // TODO: PF-2710(part 2): make a batch getOrCreatePao in TPS and use it here
    Map<UUID, TpsPaoGetResult> paoMap = new HashMap<>();
    if (features.isTpsEnabled()) {
      for (DbWorkspaceDescription dbWorkspaceDescription : dbWorkspaceDescriptions.values()) {
        TpsPaoGetResult workspacePao =
            Rethrow.onInterrupted(
                () ->
                    tpsApiDispatch.getOrCreatePao(
                        dbWorkspaceDescription.getWorkspace().workspaceId(),
                        TpsComponent.WSM,
                        TpsObjectType.WORKSPACE),
                "getOrCreatePao");
        paoMap.put(dbWorkspaceDescription.getWorkspace().workspaceId(), workspacePao);
      }
    }

    // Join the DAO workspace descriptions with the Sam info to generate a list of
    // workspace descriptions
    return dbWorkspaceDescriptions.values().stream()
        .map(
            w -> {
              AccessibleWorkspace accessibleWorkspace =
                  accessibleWorkspaces.get(w.getWorkspace().getWorkspaceId());
              TpsPaoGetResult pao = paoMap.get(w.getWorkspace().getWorkspaceId());
              return new WorkspaceDescription(
                  w.getWorkspace(),
                  accessibleWorkspace.highestRole(),
                  accessibleWorkspace.missingAuthDomainGroups(),
                  w.getLastUpdatedByEmail(),
                  w.getLastUpdatedByDate(),
                  w.getAwsCloudContext(),
                  w.getAzureCloudContext(),
                  w.getGcpCloudContext(),
                  pao);
            })
        .toList();
  }

  /** Retrieves an existing workspace by ID */
  @WithSpan
  public Workspace getWorkspace(UUID uuid) {
    return workspaceDao.getWorkspace(uuid);
  }

  @WithSpan
  public WsmIamRole getHighestRole(UUID uuid, AuthenticatedUserRequest userRequest) {
    logger.info("getHighestRole - userRequest: {}\nuserFacingId: {}", userRequest, uuid.toString());
    List<WsmIamRole> requesterRoles =
        Rethrow.onInterrupted(
            () ->
                samService.listRequesterRoles(
                    userRequest, SamConstants.SamResource.WORKSPACE, uuid.toString()),
            "listRequesterRoles");
    Optional<WsmIamRole> highestRole = WsmIamRole.getHighestRole(uuid, requesterRoles);
    Preconditions.checkState(
        highestRole.isPresent(), String.format("Workspace %s missing roles", uuid));
    return highestRole.get();
  }

  /**
   * Update an existing workspace. Currently, can change the workspace's display name or
   * description.
   *
   * @param workspaceUuid workspace of interest
   * @param name name to change - may be null
   * @param description description to change - may be null
   * @return workspace description
   */
  @WithSpan
  public WorkspaceDescription updateWorkspace(
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
    DbWorkspaceDescription dbWorkspaceDescription =
        workspaceDao.getWorkspaceDescription(workspaceUuid);
    return makeWorkspaceDescription(userRequest, dbWorkspaceDescription);
  }

  /**
   * Update an existing workspace properties.
   *
   * @param workspaceUuid workspace of interest
   * @param properties list of keys in properties
   */
  @WithSpan
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
  @WithSpan
  public void deleteWorkspace(Workspace workspace, AuthenticatedUserRequest userRequest) {
    JobBuilder deleteJob =
        buildDeleteWorkspaceJob(workspace, userRequest, UUID.randomUUID().toString(), null);
    deleteJob.submitAndWait();
  }

  /** Async delete of an existing workspace */
  @WithSpan
  public void deleteWorkspaceAsync(
      Workspace workspace,
      AuthenticatedUserRequest userRequest,
      String jobId,
      @Nullable String resultPath) {
    JobBuilder deleteJob = buildDeleteWorkspaceJob(workspace, userRequest, jobId, resultPath);
    deleteJob.submit();
  }

  private JobBuilder buildDeleteWorkspaceJob(
      Workspace workspace,
      AuthenticatedUserRequest userRequest,
      String jobId,
      @Nullable String resultPath) {
    return jobService
        .newJob()
        .jobId(jobId)
        .description("Delete workspace " + workspace.getWorkspaceId())
        .flightClass(WorkspaceDeleteFlight.class)
        .operationType(OperationType.DELETE)
        .workspaceId(workspace.getWorkspaceId().toString())
        .userRequest(userRequest)
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
  }

  /**
   * Update an existing workspace properties.
   *
   * @param workspaceUuid workspace of interest
   * @param propertyKeys list of keys in properties
   */
  @WithSpan
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

  @WithSpan
  public String cloneWorkspace(
      Workspace sourceWorkspace,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      TpsPolicyInputs additionalPolicies,
      Workspace destinationWorkspace,
      @Nullable SpendProfile spendProfile,
      @Nullable String projectOwnerGroupId) {
    UUID workspaceUuid = sourceWorkspace.getWorkspaceId();

    // Get the enabled applications from the source workspace
    List<String> applicationIds =
        applicationDao.listWorkspaceApplicationsForClone(sourceWorkspace.getWorkspaceId());

    // Find out if the source workspace needs to be linked or merged
    CloningInstructions cloningInstructions =
        (resourceDao.workspaceRequiresLinkReferences(workspaceUuid))
            ? CloningInstructions.LINK_REFERENCE
            : CloningInstructions.COPY_REFERENCE;

    // Create the destination workspace synchronously first.
    createWorkspaceForClone(
        destinationWorkspace,
        additionalPolicies,
        applicationIds,
        projectOwnerGroupId,
        workspaceUuid,
        cloningInstructions,
        userRequest);

    // Remaining steps are an async flight.
    return jobService
        .newJob()
        .description(
            String.format(
                "Clone workspace: name: '%s' id: '%s'  ",
                sourceWorkspace.getDisplayName().orElse(""), workspaceUuid))
        .flightClass(CloneWorkspaceFlight.class)
        .userRequest(userRequest)
        .request(destinationWorkspace)
        .operationType(OperationType.CLONE)
        // allow UI to watch this job (and sub-flights) from dest workspace page during clone
        .workspaceId(destinationWorkspace.getWorkspaceId().toString())
        .addParameter(
            SOURCE_WORKSPACE_ID, sourceWorkspace.getWorkspaceId()) // TODO: remove this duplication
        .addParameter(ControlledResourceKeys.LOCATION, location)
        .addParameter(SPEND_PROFILE, spendProfile)
        .submit();
  }

  /**
   * Create a cloud context
   *
   * @param workspace workspace where we want the context
   * @param cloudPlatform cloud platform of the context
   * @param spendProfile spend profile to use
   * @param jobId job id to use for the flight
   * @param userRequest user auth
   * @param resultPath result path for async responses
   */
  @WithSpan
  public void createCloudContext(
      Workspace workspace,
      CloudPlatform cloudPlatform,
      SpendProfile spendProfile,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath) {
    jobService
        .newJob()
        .description(
            String.format(
                "Create %s Cloud Context for workspace %s",
                cloudPlatform.toString(), workspace.getWorkspaceId()))
        .jobId(jobId)
        .workspaceId(workspace.getWorkspaceId().toString())
        .operationType(OperationType.CREATE)
        .flightClass(CreateCloudContextFlight.class)
        .userRequest(userRequest)
        .addParameter(SPEND_PROFILE, spendProfile)
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .addParameter(CLOUD_PLATFORM, cloudPlatform)
        .submit();
  }

  /** Delete a cloud context for the workspace. */
  @WithSpan
  public void deleteCloudContext(
      Workspace workspace, CloudPlatform cloudPlatform, AuthenticatedUserRequest userRequest) {
    var jobBuilder =
        buildDeleteCloudContextJob(
            workspace, cloudPlatform, userRequest, UUID.randomUUID().toString(), null);
    jobBuilder.submitAndWait();
  }

  @WithSpan
  public void deleteCloudContextAsync(
      Workspace workspace,
      CloudPlatform cloudPlatform,
      AuthenticatedUserRequest userRequest,
      String jobId,
      @Nullable String resultPath) {
    var jobBuilder =
        buildDeleteCloudContextJob(workspace, cloudPlatform, userRequest, jobId, resultPath);
    jobBuilder.submit();
  }

  private JobBuilder buildDeleteCloudContextJob(
      Workspace workspace,
      CloudPlatform cloudPlatform,
      AuthenticatedUserRequest userRequest,
      String jobId,
      @Nullable String resultPath) {
    return jobService
        .newJob()
        .jobId(jobId)
        .description(
            String.format(
                "Delete %s cloud context for workspace: name: '%s' id: '%s'  ",
                cloudPlatform, workspace.getDisplayName().orElse(""), workspace.getWorkspaceId()))
        .flightClass(DeleteCloudContextFlight.class)
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .workspaceId(workspace.getWorkspaceId().toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .addParameter(CLOUD_PLATFORM, cloudPlatform);
  }

  /**
   * Remove a workspace role from a user. This will also remove a user from their private resources
   * if they are no longer a member of the workspace (i.e. have no other roles) after role removal.
   *
   * <p>This method uses WSM's SA credentials to remove users from a workspace. You must validate
   * that the calling user is a workspace owner before calling this method, preferably in the
   * controller layer.
   *
   * @param workspace Workspace to remove user's role from
   * @param role Role to remove
   * @param rawUserEmail Email identifier of user whose role is being removed
   * @param executingUserRequest User credentials to authenticate this removal. Must belong to a
   *     workspace owner, and likely do not belong to {@code userEmail}.
   */
  @WithSpan
  public void removeWorkspaceRoleFromUser(
      Workspace workspace,
      WsmIamRole role,
      String rawUserEmail,
      AuthenticatedUserRequest executingUserRequest) {
    // GCP always uses lowercase email identifiers, so we do the same here.
    String targetUserEmail = rawUserEmail.toLowerCase();
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
        .submitAndWait();
  }

  /**
   * Links the policies from the source object (workspace, snapshot, etc.) to the policies of the
   * target workspace. If the source object contains a group constraint, the groups will be added to
   * the workspace's authorization domain.
   *
   * @param workspaceId Target workspace ID
   * @param sourcePaoId PAO object to link policies from
   * @param tpsUpdateMode DRY_RUN or FAIL_ON_CONFLICT
   * @param userRequest Authenticated user request
   * @return The updated PAO for the workspace
   */
  @WithSpan
  public TpsPaoUpdateResult linkPolicies(
      UUID workspaceId,
      TpsPaoDescription sourcePaoId,
      TpsUpdateMode tpsUpdateMode,
      AuthenticatedUserRequest userRequest) {
    logger.info(
        "Linking workspace policies {} to {} for {}",
        workspaceId,
        sourcePaoId.getObjectId(),
        userRequest.getEmail());
    TpsPaoGetResult paoBeforeUpdate =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.getOrCreatePao(
                    workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE),
            "getOrCreatePao");

    Rethrow.onInterrupted(
        () ->
            tpsApiDispatch.getOrCreatePao(
                sourcePaoId.getObjectId(), sourcePaoId.getComponent(), sourcePaoId.getObjectType()),
        "getOrCreatePao");

    TpsPaoUpdateResult dryRun =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.linkPao(
                    workspaceId, sourcePaoId.getObjectId(), TpsUpdateMode.DRY_RUN),
            "linkPao");

    if (!dryRun.getConflicts().isEmpty() && tpsUpdateMode != TpsUpdateMode.DRY_RUN) {
      throw new PolicyConflictException(
          "Workspace policies conflict with source", dryRun.getConflicts());
    }

    policyValidator.validateWorkspaceConformsToPolicy(
        getWorkspace(workspaceId), dryRun.getResultingPao(), userRequest);

    if (tpsUpdateMode == TpsUpdateMode.DRY_RUN) {
      return dryRun;
    } else {
      var updateResult =
          Rethrow.onInterrupted(
              () -> tpsApiDispatch.linkPao(workspaceId, sourcePaoId.getObjectId(), tpsUpdateMode),
              "linkPao");
      if (Boolean.TRUE.equals(updateResult.isUpdateApplied())) {
        workspaceActivityLogService.writeActivity(
            userRequest,
            workspaceId,
            OperationType.UPDATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.POLICIES);
        logger.info(
            "Finished linking workspace policies {} to {} for {}",
            workspaceId,
            sourcePaoId.getObjectId(),
            userRequest.getEmail());
      } else {
        logger.warn(
            "Failed linking workspace policies {} to {} for {}",
            workspaceId,
            sourcePaoId.getObjectId(),
            userRequest.getEmail());
      }

      patchWorkspaceAuthDomain(workspaceId, userRequest, paoBeforeUpdate, dryRun);

      return updateResult;
    }
  }

  @WithSpan
  public TpsPaoUpdateResult updatePolicy(
      UUID workspaceUuid,
      TpsPolicyInputs addAttributes,
      TpsPolicyInputs removeAttributes,
      TpsUpdateMode updateMode,
      AuthenticatedUserRequest userRequest) {
    logger.info("Updating workspace policies {} for {}", workspaceUuid, userRequest.getEmail());
    TpsPaoGetResult paoBeforeUpdate =
        Rethrow.onInterrupted(() -> tpsApiDispatch.getPao(workspaceUuid), "getPao");

    var dryRun =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.updatePao(
                    workspaceUuid, addAttributes, removeAttributes, TpsUpdateMode.DRY_RUN),
            "updatePao");

    if (!dryRun.getConflicts().isEmpty() && updateMode != TpsUpdateMode.DRY_RUN) {
      throw new PolicyConflictException(
          "Workspace policies conflict with policy updates", dryRun.getConflicts());
    }

    policyValidator.validateWorkspaceConformsToPolicy(
        getWorkspace(workspaceUuid), dryRun.getResultingPao(), userRequest);

    if (updateMode == TpsUpdateMode.DRY_RUN) {
      return dryRun;
    } else {
      var result =
          Rethrow.onInterrupted(
              () ->
                  tpsApiDispatch.updatePao(
                      workspaceUuid, addAttributes, removeAttributes, updateMode),
              "updatePao");

      if (Boolean.TRUE.equals(result.isUpdateApplied())) {
        workspaceActivityLogService.writeActivity(
            userRequest,
            workspaceUuid,
            OperationType.UPDATE,
            workspaceUuid.toString(),
            ActivityLogChangedTarget.POLICIES);
        logger.info(
            "Finished updating workspace policies {} for {}",
            workspaceUuid,
            userRequest.getEmail());
      } else {
        logger.warn(
            "Workspace policies update failed to apply to {} for {}",
            workspaceUuid,
            userRequest.getEmail());
      }

      patchWorkspaceAuthDomain(workspaceUuid, userRequest, paoBeforeUpdate, dryRun);

      return result;
    }
  }

  private void patchWorkspaceAuthDomain(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      TpsPaoGetResult paoBeforeUpdate,
      TpsPaoUpdateResult dryRun) {
    HashSet<String> addedGroups =
        TpsUtilities.getAddedGroups(paoBeforeUpdate, dryRun.getResultingPao());
    if (!addedGroups.isEmpty()) {
      logger.info(
          "Group policies have changed, adding additional groups to auth domain in Sam for workspace {}",
          workspaceUuid);
      Rethrow.onInterrupted(
          () ->
              samService.addGroupsToAuthDomain(
                  userRequest,
                  SamConstants.SamResource.WORKSPACE,
                  workspaceUuid.toString(),
                  addedGroups.stream().toList()),
          "updateAuthDomains");
    }
  }
}
