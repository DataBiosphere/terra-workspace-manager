package bio.terra.workspace.service.workspace.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class CreateGoogleContextFlightTest extends BaseConnectedTest {

  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @MockBean private SamService mockSamService;
  @Autowired private UserAccessUtils userAccessUtils;

  @BeforeEach
  void setUp() throws InterruptedException {
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
    // Return a valid google group for cloud sync, as Google validates groups added to GCP projects.
    Mockito.when(mockSamService.syncWorkspacePolicy(any(), any(), any()))
        .thenReturn("terra-workspace-manager-test-group@googlegroups.com");
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void successCreatesProjectAndContext() throws Exception {
    UUID workspaceId = createWorkspace(spendUtils.defaultSpendId());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = getStepNameToStepStatusMap();
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);

    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isPresent());

    String contextProjectId =
        workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);
    assertEquals(projectId, contextProjectId);

    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals(
        "billingAccounts/" + spendUtils.defaultBillingAccountId(),
        crl.getCloudBillingClientCow()
            .getProjectBillingInfo("projects/" + projectId)
            .getBillingAccountName());
    assertRolesExist(project);
    assertPolicyGroupsSynced(workspaceId, project);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createsProjectAndContext_noBillingAccount_flightFailsAndGcpProjectNotCreated()
      throws Exception {
    UUID workspaceId = createWorkspace(spendUtils.noBillingAccount());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            FlightDebugInfo.newBuilder().build());

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertEquals(NoBillingAccountException.class, flightState.getException().get().getClass());
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());
    assertFalse(
        flightState.getResultMap().get().containsKey(WorkspaceFlightMapKeys.GCP_PROJECT_ID));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createsProjectAndContext_emptySpendProfile_flightFailsAndGcpProjectNotCreated()
      throws Exception {
    UUID workspaceId = createWorkspace(null);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            FlightDebugInfo.newBuilder().build());

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertEquals(MissingSpendProfileException.class, flightState.getException().get().getClass());
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());
    assertFalse(
        flightState.getResultMap().get().containsKey(WorkspaceFlightMapKeys.GCP_PROJECT_ID));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createsProjectAndContext_unauthorizedSpendProfile_flightFailsAndGcpProjectNotCreated()
      throws Exception {
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(false);
    UUID workspaceId = createWorkspace(spendUtils.defaultSpendId());
    AuthenticatedUserRequest unauthorizedUserRequest = userAccessUtils.secondUserAuthRequest();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, unauthorizedUserRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            FlightDebugInfo.newBuilder().build());

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertEquals(SpendUnauthorizedException.class, flightState.getException().get().getClass());
    assertFalse(
        flightState.getResultMap().get().containsKey(WorkspaceFlightMapKeys.GCP_PROJECT_ID));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void errorRevertsChanges() throws Exception {
    UUID workspaceId = createWorkspace(spendUtils.defaultSpendId());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    // Submit a flight class that always errors.
    Map<String, StepStatus> retrySteps = getStepNameToStepStatusMap();
    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    // The Project should exist, but requested to be deleted.
    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  private Map<String, StepStatus> getStepNameToStepStatusMap() {
    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(PullProjectFromPoolStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(SetProjectBillingStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateCustomGcpRolesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(StoreGcpContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(SyncSamGroupsStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    return retrySteps;
  }

  /** Creates a workspace, returning its workspaceId. */
  private UUID createWorkspace(@Nullable SpendProfileId spendProfileId) {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(Optional.ofNullable(spendProfileId))
            .build();
    return workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }

  /** Create the FlightMap input parameters required for the {@link CreateGcpContextFlight}. */
  private static FlightMap createInputParameters(
      UUID workspaceId, AuthenticatedUserRequest userRequest) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  /**
   * Asserts that a provided project has every custom role specified in {@link
   * CustomGcpIamRoleMapping}
   */
  private void assertRolesExist(Project project) throws IOException {
    for (CustomGcpIamRole customRole :
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values()) {
      String fullRoleName = customRole.getFullyQualifiedRoleName(project.getProjectId());
      Role gcpRole = crl.getIamCow().projects().roles().get(fullRoleName).execute();
      assertEquals(customRole.getRoleName(), gcpRole.getTitle());

      // Role.getIncludedPermissions returns null instead of an empty list, so we handle that here.
      List<String> gcpPermissions =
          Optional.ofNullable(gcpRole.getIncludedPermissions()).orElse(Collections.emptyList());
      assertThat(gcpPermissions, containsInAnyOrder(customRole.getIncludedPermissions().toArray()));
    }
  }

  /** Asserts that Sam groups are granted their appropriate IAM roles on a GCP project. */
  private void assertPolicyGroupsSynced(UUID workspaceId, Project project) throws Exception {
    Map<WsmIamRole, String> roleToSamGroup =
        Arrays.stream(WsmIamRole.values())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    role ->
                        "group:"
                            + SamRethrow.onInterrupted(
                                () ->
                                    mockSamService.syncWorkspacePolicy(
                                        workspaceId,
                                        role,
                                        userAccessUtils.defaultUserAuthRequest()),
                                "syncWorkspacePolicy")));
    Policy currentPolicy =
        crl.getCloudResourceManagerCow()
            .projects()
            .getIamPolicy(project.getProjectId(), new GetIamPolicyRequest())
            .execute();
    for (WsmIamRole role : WsmIamRole.values()) {
      assertRoleBindingInPolicy(
          role, roleToSamGroup.get(role), currentPolicy, project.getProjectId());
    }
  }

  /**
   * Validate that a GCP policy contains the expected role binding for the given role.
   *
   * @param role An IAM role. Maps to an expected GCP role in CloudSyncRoleMapping.
   * @param groupEmail The group we expect the role to be bound to.
   * @param gcpPolicy The GCP policy we're checking for the role binding.
   * @param projectId The GCP project our custom roles are defined in.
   */
  private void assertRoleBindingInPolicy(
      WsmIamRole role, String groupEmail, Policy gcpPolicy, String projectId) {
    String expectedGcpRoleName =
        CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
            .get(role)
            .getFullyQualifiedRoleName(projectId);
    List<Binding> actualGcpBindingList = gcpPolicy.getBindings();
    List<String> actualGcpRoleList =
        actualGcpBindingList.stream().map(Binding::getRole).collect(Collectors.toList());
    assertTrue(actualGcpRoleList.contains(expectedGcpRoleName));
    assertTrue(
        actualGcpBindingList
            .get(actualGcpRoleList.indexOf(expectedGcpRoleName))
            .getMembers()
            .contains(groupEmail));
  }
}
