package bio.terra.workspace.service.workspace;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneGcpWorkspaceFlight;
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
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.common.collect.ImmutableList;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  private final SamService samService;
  private final SpendProfileService spendProfileService;
  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final StageService stageService;
  private final CrlService crlService;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      SpendProfileService spendProfileService,
      BufferServiceConfiguration bufferServiceConfiguration,
      StageService stageService,
      CrlService crlService) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.stageService = stageService;
    this.crlService = crlService;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @Traced
  public UUID createWorkspace(
      WorkspaceRequest workspaceRequest, AuthenticatedUserRequest userRequest) {

    String description = "Create workspace " + workspaceRequest.workspaceId().toString();
    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                WorkspaceCreateFlight.class,
                null,
                userRequest)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceRequest.workspaceId().toString());
    if (workspaceRequest.spendProfileId().isPresent()) {
      createJob.addParameter(
          WorkspaceFlightMapKeys.SPEND_PROFILE_ID, workspaceRequest.spendProfileId().get().id());
    }

    createJob.addParameter(
        WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspaceRequest.workspaceStage().name());

    createJob.addParameter(
        WorkspaceFlightMapKeys.DISPLAY_NAME, workspaceRequest.displayName().orElse(""));
    createJob.addParameter(
        WorkspaceFlightMapKeys.DESCRIPTION, workspaceRequest.description().orElse(""));
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
   * @param userRequest the user's authenticated request
   * @param workspaceId id of the workspace in question
   * @param action the action to authorize against the workspace
   * @return the workspace, if it exists and the user is permitted to perform the specified action.
   */
  @Traced
  public Workspace validateWorkspaceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String action) {
    logger.info(
        "validateWorkspaceAndAction - userRequest: {}\nworkspaceId: {}\naction: {}",
        userRequest,
        workspaceId,
        action);
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest, SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString(), action),
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
  public Workspace getWorkspace(UUID id, AuthenticatedUserRequest userRequest) {
    return validateWorkspaceAndAction(userRequest, id, SamConstants.SAM_WORKSPACE_READ_ACTION);
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
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String name,
      @Nullable String description) {
    validateWorkspaceAndAction(userRequest, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    workspaceDao.updateWorkspace(workspaceId, name, description);
    return workspaceDao.getWorkspace(workspaceId);
  }

  /** Delete an existing workspace by ID. */
  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(userRequest, id, SamConstants.SAM_WORKSPACE_DELETE_ACTION);

    String description = "Delete workspace " + id;
    JobBuilder deleteJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                WorkspaceDeleteFlight.class,
                null, // Delete does not have a useful request body
                userRequest)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, id.toString())
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
    deleteJob.submitAndWait(null);
  }

  /**
   * Process the request to create a GCP cloud context
   *
   * @param workspaceId workspace in which to create the context
   * @param jobId caller-supplied job id of the async job
   * @param userRequest user authentication info
   * @param resultPath optional endpoint where the result of the completed job can be retrieved
   */
  @Traced
  public void createGcpCloudContext(
      UUID workspaceId,
      String jobId,
      AuthenticatedUserRequest userRequest,
      @Nullable String resultPath) {

    if (!bufferServiceConfiguration.getEnabled()) {
      throw new BufferServiceDisabledException(
          "Cannot create a GCP context in an environment where buffer service is disabled or not configured.");
    }

    Workspace workspace =
        validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    stageService.assertMcWorkspace(workspace, "createCloudContext");

    // TODO: We should probably do this in a step of the job. It will be talking to another
    //  service and that may require retrying. It also may be slow, so getting it off of this
    //  thread and getting our response back might be better.
    SpendProfileId spendProfileId =
        workspace
            .getSpendProfileId()
            .orElseThrow(() -> new MissingSpendProfileException(workspaceId));
    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId, userRequest);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw new NoBillingAccountException(spendProfileId);
    }

    jobService
        .newJob(
            "Create GCP Cloud Context " + workspaceId,
            jobId,
            CreateGcpContextFlight.class,
            /* request= */ null,
            userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString())
        .addParameter(
            WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendProfile.billingAccountId().get())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .submit();
  }

  public void createGcpCloudContext(
      UUID workspaceId, String jobId, AuthenticatedUserRequest userRequest) {
    createGcpCloudContext(workspaceId, jobId, userRequest, null);
  }

  public String cloneWorkspace(
      UUID sourceWorkspaceId,
      AuthenticatedUserRequest userRequest,
      String spendProfile,
      @Nullable String location,
      @Nullable String displayName,
      @Nullable String description) {
    final Workspace sourceWorkspace =
        validateWorkspaceAndAction(
            userRequest, sourceWorkspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    stageService.assertMcWorkspace(sourceWorkspace, "cloneGcpWorkspace");

    return jobService
        .newJob(
            "Clone GCP Workspace " + sourceWorkspaceId.toString(),
            UUID.randomUUID().toString(),
            CloneGcpWorkspaceFlight.class,
            null,
            userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, sourceWorkspaceId)
        .addParameter(WorkspaceFlightMapKeys.DISPLAY_NAME, displayName)
        .addParameter(WorkspaceFlightMapKeys.DESCRIPTION, description)
        .addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, spendProfile)
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
  public void deleteGcpCloudContext(UUID workspaceId, AuthenticatedUserRequest userRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    stageService.assertMcWorkspace(workspace, "deleteGcpCloudContext");
    jobService
        .newJob(
            "Delete GCP Context " + workspaceId,
            UUID.randomUUID().toString(),
            DeleteGcpContextFlight.class,
            /* request= */ null,
            userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString())
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

  /**
   * Helper method for looking up the GCP project ID for a given workspace ID, if one exists. Unlike
   * {@link #getRequiredGcpProject(UUID)}, this returns an empty Optional instead of throwing if the
   * given workspace does not have a GCP cloud context.
   */
  public Optional<String> getGcpProject(UUID workspaceId) {
    return workspaceDao
        .getWorkspace(workspaceId)
        .getGcpCloudContext()
        .map(GcpCloudContext::getGcpProjectId);
  }

  /**
   * Grant a user permission to impersonate their pet service account in a given workspace. Unlike
   * other operations, this does not run in a flight because it only requires one write operation.
   * This operation is idempotent.
   *
   * @return The email identifier of the user's pet SA in the given workspace.
   */
  public String enablePetServiceAccountImpersonation(
      UUID workspaceId, AuthenticatedUserRequest userRequest) {
    final String serviceAccountUserRole = "roles/iam.serviceAccountUser";
    // Validate that the user is a member of the workspace.
    Workspace workspace =
        validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    stageService.assertMcWorkspace(workspace, "enablePet");

    // getEmailFromToken will always call Sam, which will return a user email even if the requesting
    // access token belongs to a pet SA.
    String userEmail =
        SamRethrow.onInterrupted(
            () -> samService.getRequestUserEmail(userRequest), "getRequestUserEmail");
    String projectId = getRequiredGcpProject(workspaceId);
    String petSaEmail = samService.getOrCreatePetSaEmail(projectId, userRequest);
    ServiceAccountName petSaName =
        ServiceAccountName.builder().email(petSaEmail).projectId(projectId).build();
    try {
      Policy saPolicy =
          crlService.getIamCow().projects().serviceAccounts().getIamPolicy(petSaName).execute();
      // TODO(PF-991): In the future, the pet SA should not be included in this binding. This is a
      //  workaround to support the CLI and other applications which call the GCP Pipelines API with
      //  the pet SA's credentials.
      Binding saUserBinding =
          new Binding()
              .setRole(serviceAccountUserRole)
              .setMembers(ImmutableList.of("user:" + userEmail, "serviceAccount:" + petSaEmail));
      // If no bindings exist, getBindings() returns null instead of an empty list.
      List<Binding> bindingList =
          Optional.ofNullable(saPolicy.getBindings()).orElse(new ArrayList<>());
      // GCP automatically de-duplicates bindings, so this will have no effect if the user already
      // has permission to use their pet service account.
      bindingList.add(saUserBinding);
      saPolicy.setBindings(bindingList);
      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      crlService
          .getIamCow()
          .projects()
          .serviceAccounts()
          .setIamPolicy(petSaName, request)
          .execute();
      return petSaEmail;
    } catch (IOException e) {
      throw new RuntimeException("Error enabling user's pet SA", e);
    }
  }

  /**
   * Remove a workspace role from a user. This will also remove a user from their private resources
   * if they are no longer a member of the workspace (i.e. have no other roles) after role removal.
   *
   * @param workspaceId ID of the workspace user to remove user's role in
   * @param role Role to remove
   * @param targetUserEmail Email identifier of user whose role is being removed
   * @param executingUserRequest User credentials to authenticate this removal. Must belong to a
   *     workspace owner, and likely do not belong to {@code userEmail}.
   */
  public void removeWorkspaceRoleFromUser(
      UUID workspaceId,
      WsmIamRole role,
      String targetUserEmail,
      AuthenticatedUserRequest executingUserRequest) {
    Workspace workspace =
        validateWorkspaceAndAction(
            executingUserRequest, workspaceId, SamConstants.SAM_WORKSPACE_OWN_ACTION);
    stageService.assertMcWorkspace(workspace, "removeWorkspaceRoleFromUser");
    // Before launching the flight, validate that the user being removed is a direct member of the
    // specified role. Users may also be added to a workspace via managed groups, but WSM does not
    // control membership of those groups, and so cannot remove them here.
    // All emails are compared as lowercase strings.
    List<String> roleMembers =
        samService.listUsersWithWorkspaceRole(workspaceId, role, executingUserRequest).stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    if (!roleMembers.contains(targetUserEmail.toLowerCase())) {
      return;
    }
    jobService
        .newJob(
            String.format(
                "Remove role %s from user %s in workspace %s",
                role.name(), targetUserEmail, workspaceId),
            UUID.randomUUID().toString(),
            RemoveUserFromWorkspaceFlight.class,
            /* request= */ null,
            executingUserRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString())
        .addParameter(WorkspaceFlightMapKeys.USER_TO_REMOVE, targetUserEmail)
        .addParameter(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, role)
        .submitAndWait(null);
  }
}
