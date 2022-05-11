package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DeleteAzureContextFlightTest extends BaseAzureTest {
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
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private JobService jobService;
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @Autowired private AzureTestUtils azureTestUtils;

  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    // Create a new workspace at the start of each test.
    UUID uuid = UUID.randomUUID();
    Workspace request =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId("a" + uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();
    workspaceUuid =
        workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspaceUuid, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  void deleteContextDo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    FlightMap createParameters =
        azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest);

    // Create the azure context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    AzureCloudContext azureCloudContext =
        workspaceService.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).orElse(null);
    assertNotNull(azureCloudContext);

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Force each step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteControlledAzureResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(testUtils.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isEmpty());
  }

  @Test
  void deleteContextUndo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    FlightMap createParameters =
        azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest);

    // Create the azure context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

    // Fail at the end of the flight to verify it can't be undone.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Because this flight cannot be undone, the context should still be deleted even after undoing.
    assertTrue(testUtils.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isEmpty());
  }

  @Test
  void deleteNonExistentContextIsOk() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(testUtils.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isEmpty());

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
    assertTrue(testUtils.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isEmpty());
  }

  @Test
  void deleteResourcesInContext() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    FlightMap createParameters =
        azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest);

    // Create the azure context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    AzureCloudContext azureCloudContext =
        workspaceService.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).orElse(null);
    assertNotNull(azureCloudContext);

    // Create Azure relay namespace
    UUID relayId = UUID.randomUUID();
    ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();
    ControlledAzureRelayNamespaceResource relayNamespaceResource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(relayId)
                    .name("wsm-test" + relayId.toString())
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .namespaceName(creationParameters.getNamespaceName())
            .region(creationParameters.getRegion())
            .build();

    controlledResourceService.createAzureRelayNamespace(
        relayNamespaceResource, creationParameters, null, null, null, userRequest);
    ControlledAzureRelayNamespaceResource gotRelayNamespaceResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, relayId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);

    assertEquals(relayNamespaceResource, gotRelayNamespaceResource);

    // Delete the azure context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Force each step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteControlledAzureResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteAzureContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(testUtils.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(workspaceUuid, relayId, userRequest));
  }

  // todo: this test would be better in the WorkspaceDeleteFlightTest, but that class extends
  // BaseConnectedTest which doesn't have azure enabled... figure out what to do about this test
  @Test
  void deleteMcWorkspaceWithAzureContextAndResource() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // create new workspace so delete at end of test won't interfere with @AfterEach teardown
    UUID uuid = UUID.randomUUID();
    Workspace request =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId("a" + uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();
    UUID mcWorkspaceUuid = workspaceService.createWorkspace(request, userRequest);

    // Create the azure context.
    FlightMap createParameters =
        azureTestUtils.createAzureContextInputParameters(mcWorkspaceUuid, userRequest);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    AzureCloudContext azureCloudContext =
        workspaceService.getAuthorizedAzureCloudContext(mcWorkspaceUuid, userRequest).orElse(null);
    assertNotNull(azureCloudContext);

    // Create Azure relay namespace
    UUID relayId = UUID.randomUUID();
    ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();
    ControlledAzureRelayNamespaceResource relayNamespaceResource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(mcWorkspaceUuid)
                    .resourceId(relayId)
                    .name(UUID.randomUUID().toString())
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .namespaceName(creationParameters.getNamespaceName())
            .region(creationParameters.getRegion())
            .build();
    controlledResourceService.createAzureRelayNamespace(
        relayNamespaceResource, creationParameters, null, null, null, userRequest);
    ControlledAzureRelayNamespaceResource gotRelayNamespaceResource =
        controlledResourceService
            .getControlledResource(mcWorkspaceUuid, relayId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);

    assertEquals(relayNamespaceResource, gotRelayNamespaceResource);

    // Run the delete flight, retrying every step once
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, mcWorkspaceUuid.toString());
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.MC_WORKSPACE);
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteControlledAzureResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(
        DeleteControlledSamResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteGcpProjectStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteAzureContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceStateStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            WorkspaceDeleteFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify the resource and workspace are not in WSM DB
    assertThrows(
        WorkspaceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                relayNamespaceResource.getWorkspaceId(),
                relayNamespaceResource.getResourceId(),
                userRequest));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(mcWorkspaceUuid, userRequest));
  }
}
