package bio.terra.workspace.service.workspace.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.WorkspaceService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

class CreateGoogleContextFlightTest extends BaseConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private SamService samService;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void successCreatesProjectAndContext() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    assertTrue(workspaceService.getGcpCloudContext(workspaceId).isEmpty());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, spendUtils.defaultBillingAccountId(), userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);

    assertTrue(workspaceService.getGcpCloudContext(workspaceId).isPresent());

    String contextProjectId = workspaceService.getRequiredGcpProject(workspaceId);
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
  void errorRevertsChanges() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getGcpCloudContext(workspaceId).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createInputParameters(workspaceId, spendUtils.defaultBillingAccountId(), userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(workspaceService.getGcpCloudContext(workspaceId).isEmpty());

    String projectId =
        flightState.getResultMap().get().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    // The Project should exist, but requested to be deleted.
    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  /** Creates a workspace, returning its workspaceId. */
  private UUID createWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }

  /** Create the FlightMap input parameters required for the {@link CreateGcpContextFlight}. */
  private static FlightMap createInputParameters(
      UUID workspaceId, String billingAccountId, AuthenticatedUserRequest userRequest) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, billingAccountId);
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
                                    samService.syncWorkspacePolicy(
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
