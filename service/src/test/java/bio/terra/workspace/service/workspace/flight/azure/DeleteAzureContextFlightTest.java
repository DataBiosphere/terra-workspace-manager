package bio.terra.workspace.service.workspace.flight.azure;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.*;
import bio.terra.workspace.service.workspace.flight.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.azure.DeleteAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.azure.DeleteAzureContextStep;
import bio.terra.workspace.service.workspace.flight.gcp.DeleteGcpProjectStep;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnectedPlus")
public class DeleteAzureContextFlightTest extends BaseAzureConnectedTest {
  /**
   * How long to wait for a delete context Stairway flight to complete before timing out the test.
   */
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);
  /**
   * How long to wait for a create context Stairway flight to complete before timing out the test.
   */
  private static final Duration CREATION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private JobService jobService;
  @Autowired private AzureCloudContextService azureCloudContextService;

  private Workspace workspace;
  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    // Create a new workspace at the start of each test.
    workspaceUuid = UUID.randomUUID();
    SpendProfileId spendProfileId = initSpendProfileMock();
    workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build();
    workspaceService.createWorkspace(
        workspace, null, null, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
  }

  private void createAzureContext(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
      throws Exception {
    FlightMap createParameters =
        azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest);

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // This call validates that an Azure cloud context is present.
    azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
  }

  private UUID createAzureIpResource(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    final UUID ipId = UUID.randomUUID();
    ControlledAzureIpResource ipResource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(ipId)
                    .name("wsm-test" + ipId)
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .ipName(ipCreationParameters.getName())
            .region(ipCreationParameters.getRegion())
            .build();

    // Submit an IP creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, ipResource),
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    return ipId;
  }

  @Test
  void deleteContextDo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createAzureContext(workspaceUuid, userRequest);

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Force each retryable step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());
  }

  @Test
  void deleteContextUndo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createAzureContext(workspaceUuid, userRequest);

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

    // Fail at the end of the flight to verify it can't be undone.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Because this flight cannot be undone, the context should still be deleted even after undoing.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  void deleteNonExistentContextIsOk() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  void deleteResourcesInContext() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createAzureContext(workspaceUuid, userRequest);
    UUID ipId = createAzureIpResource(workspaceUuid, userRequest);

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Force each retryable step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(workspaceUuid, ipId));
  }

  // This test would be better in the WorkspaceDeleteFlightTest, but that class extends
  // BaseConnectedTest which doesn't have azure enabled so it lives here for now. If/when test
  // structure is re-evaluated and BaseAzureTest goes away, this test should be moved
  @Test
  void deleteMcWorkspaceWithAzureContextAndResource() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // create new workspace so delete at end of test won't interfere with @AfterEach teardown
    UUID uuid = UUID.randomUUID();
    SpendProfileId spendProfileId = initSpendProfileMock();
    Workspace request =
        WorkspaceFixtures.defaultWorkspaceBuilder(uuid).spendProfileId(spendProfileId).build();
    UUID mcWorkspaceUuid = workspaceService.createWorkspace(request, null, null, userRequest);

    createAzureContext(mcWorkspaceUuid, userRequest);
    UUID ipId = createAzureIpResource(mcWorkspaceUuid, userRequest);

    // Run the delete flight, retrying every retryable step once
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, mcWorkspaceUuid.toString());
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.MC_WORKSPACE);
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteControlledSamResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteGcpProjectStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceStateStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            WorkspaceDeleteFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify the resource and workspace are not in WSM DB
    assertThrows(
        ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(mcWorkspaceUuid, ipId));
    assertThrows(
        WorkspaceNotFoundException.class, () -> workspaceService.getWorkspace(mcWorkspaceUuid));
  }
}
