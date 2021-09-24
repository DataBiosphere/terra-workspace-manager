package bio.terra.workspace.service.workspace.flight.create.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreateAzureContextFlightTest extends BaseAzureTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private SamService samService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private AzureTestConfiguration azureTestConfiguration;

  @Test
  void successCreatesProjectAndContext() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    AzureCloudContext azureCloudContext =
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).get();

    assertEquals(azureCloudContext, azureTestUtils.getAzureCloudContext());
  }

  @Test
  void errorRevertsChanges() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());
  }

  /** Creates a workspace, returning its workspaceId. */
  private UUID createWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, azureTestUtils.defaultUserAuthRequest());
  }

  /** Create the FlightMap input parameters required for the {@link CreateGcpContextFlight}. */
  private FlightMap createInputParameters(UUID workspaceId, AuthenticatedUserRequest userRequest) {
    AzureCloudContext azureCloudContext = azureTestUtils.getAzureCloudContext();
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.REQUEST.getKeyName(), azureCloudContext);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }
}
