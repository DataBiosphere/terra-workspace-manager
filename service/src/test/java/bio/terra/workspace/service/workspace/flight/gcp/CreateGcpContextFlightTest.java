package bio.terra.workspace.service.workspace.flight.gcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.MakeFlightException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.CreateCustomGcpRolesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.CreatePetSaStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.GcpCloudSyncStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.GrantWsmRoleAdminStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.PullProjectFromPoolStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.SetProjectBillingStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.WaitForProjectPermissionsStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFinishStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextStartStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
class CreateGcpContextFlightTest extends BaseConnectedTest {

  /**
   * How long to wait for a Stairway flight to complete before timing out the test. This is set to
   * 20 minutes to allow tests to ride through service outages, cloud retries, and IAM propagation.
   */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(20);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceConnectedTestUtils workspaceConnectedTestUtils;
  @Autowired private GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private SamService samService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void successCreatesProjectAndContext() throws Exception {
    UUID workspaceUuid = createWorkspace(spendUtils.defaultSpendId());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = getStepNameToStepStatusMap();
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();

    FlightMap inputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.GCP, spendUtils.defaultGcpSpendProfile());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            inputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isPresent());

    String contextProjectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    assertEquals(projectId, contextProjectId);

    // Verify that the policies were properly stored
    Optional<GcpCloudContext> optionalCloudContext =
        gcpCloudContextService.getGcpCloudContext(workspaceUuid);
    assertTrue(optionalCloudContext.isPresent(), "has cloud context");
    GcpCloudContext cloudContext = optionalCloudContext.get();

    assertNotNull(cloudContext.getSamPolicyOwner(), "has owner policy");
    assertNotNull(cloudContext.getSamPolicyWriter(), "has writer policy");
    assertNotNull(cloudContext.getSamPolicyReader(), "has reader policy");
    assertNotNull(cloudContext.getSamPolicyApplication(), "has application policy");

    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals(
        "billingAccounts/" + spendUtils.defaultBillingAccountId(),
        crl.getCloudBillingClientCow()
            .getProjectBillingInfo("projects/" + projectId)
            .getBillingAccountName());
    assertRolesExist(project);
    assertPolicyGroupsSynced(workspaceUuid, project);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createsProjectAndContext_emptySpendProfile_flightFailsAndGcpProjectNotCreated() {
    UUID workspaceUuid = createWorkspace(null);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    FlightMap inputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.GCP, /* spendProfile= */ null);

    assertThrows(
        MakeFlightException.class,
        () ->
            StairwayTestUtils.blockUntilFlightCompletes(
                jobService.getStairway(),
                CreateCloudContextFlight.class,
                inputs,
                STAIRWAY_FLIGHT_TIMEOUT,
                FlightDebugInfo.newBuilder().build()));

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void errorRevertsChanges() throws Exception {
    UUID workspaceUuid = createWorkspace(spendUtils.defaultSpendId());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    // Submit a flight class that always errors.
    Map<String, StepStatus> undoRetrySteps = getStepNameToStepStatusMap();
    // Cause a failure on the penultimate step. Cannot fail after the final step,
    // because it causes a dismal failure.
    Map<String, StepStatus> doErrorStep =
        Map.of(WaitForProjectPermissionsStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder()
            .doStepFailures(doErrorStep)
            .undoStepFailures(undoRetrySteps)
            .build();

    FlightMap inputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.GCP, spendUtils.defaultGcpSpendProfile());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            inputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    // The Project should exist, but requested to be deleted.
    workspaceConnectedTestUtils.assertProjectIsBeingDeleted(projectId);
  }

  private Map<String, StepStatus> getStepNameToStepStatusMap() {
    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CreateCloudContextStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(PullProjectFromPoolStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(SetProjectBillingStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GrantWsmRoleAdminStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateCustomGcpRolesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreatePetSaStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(SyncSamGroupsStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateCloudContextFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    return retrySteps;
  }

  /**
   * Creates a workspace, returning its workspaceUuid.
   *
   * <p>Because the tests in this class mock Sam and Janitor service cleans up GCP projects, we do
   * not need to explicitly clean up the workspaces created here.
   */
  private UUID createWorkspace(@Nullable SpendProfileId spendProfileId) {
    Workspace request =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(spendProfileId)
            .build();
    return workspaceService.createWorkspace(
        request, null, null, null, userAccessUtils.defaultUserAuthRequest());
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
  private void assertPolicyGroupsSynced(UUID workspaceUuid, Project project) throws Exception {
    // Create a list of the roles which are synced to google groups in Sam
    List<WsmIamRole> syncedRoles =
        Arrays.stream(WsmIamRole.values())
            .filter(
                r ->
                    r != WsmIamRole.DISCOVERER
                        && r != WsmIamRole.MANAGER
                        && r != WsmIamRole.PROJECT_OWNER)
            .toList();
    Map<WsmIamRole, String> roleToSamGroup =
        syncedRoles.stream()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    role ->
                        "group:"
                            + Rethrow.onInterrupted(
                                () ->
                                    samService.syncWorkspacePolicy(
                                        workspaceUuid,
                                        role,
                                        userAccessUtils.defaultUserAuthRequest()),
                                "syncWorkspacePolicy")));
    Policy currentPolicy =
        crl.getCloudResourceManagerCow()
            .projects()
            .getIamPolicy(project.getProjectId(), new GetIamPolicyRequest())
            .execute();
    for (WsmIamRole role : syncedRoles) {
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
        gcpCloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(role)
            .getFullyQualifiedRoleName(projectId);
    List<Binding> actualGcpBindingList = gcpPolicy.getBindings();
    List<String> actualGcpRoleList = actualGcpBindingList.stream().map(Binding::getRole).toList();
    assertTrue(actualGcpRoleList.contains(expectedGcpRoleName));
    assertTrue(
        actualGcpBindingList
            .get(actualGcpRoleList.indexOf(expectedGcpRoleName))
            .getMembers()
            .contains(groupEmail));
  }
}
