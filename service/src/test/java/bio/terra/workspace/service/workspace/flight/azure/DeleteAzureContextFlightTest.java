package bio.terra.workspace.service.workspace.flight.azure;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFinishStep;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextStartStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.management.Region;
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
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private JobService jobService;
  @Autowired private AzureCloudContextService azureCloudContextService;

  private Workspace workspace;
  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    createLandingZone();

    // Create a new workspace at the start of each test.
    workspaceUuid = UUID.randomUUID();
    SpendProfileId spendProfileId = initSpendProfileMock();
    workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build();
    workspaceService.createWorkspace(
        workspace, null, null, null, userAccessUtils.defaultUserAuthRequest());
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
            CreateCloudContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // This call validates that an Azure cloud context is present.
    azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
  }

  private UUID createAzureResource(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
      throws Exception {
    var creationParameters = ControlledAzureResourceFixtures.getAzureDiskCreationParameters();

    UUID id = UUID.randomUUID();
    var azureResource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(id)
                    .name("wsm-test-" + id)
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .region(Region.US_EAST2.name())
                    .build())
            .diskName(creationParameters.getName())
            .size(creationParameters.getSize())
            .build();

    // Submit an IP creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, azureResource),
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    return id;
  }

  @Test
  void deleteContextDo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createAzureContext(workspaceUuid, userRequest);

    // Delete the azure context.
    FlightMap deleteParameters =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.AZURE);

    // Force each retryable step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteCloudContextStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    doFailures.put(
        DeleteCloudContextFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
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
    FlightMap deleteParameters =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.AZURE);

    // Fail at the end of the flight to verify it can't be undone.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Because this flight cannot be undone, the context should still be deleted even after undoing.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  void deleteNonExistentContext() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());

    // Delete the non-existent azure context.
    FlightMap deleteParameters =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.AZURE);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isEmpty());
  }
}
