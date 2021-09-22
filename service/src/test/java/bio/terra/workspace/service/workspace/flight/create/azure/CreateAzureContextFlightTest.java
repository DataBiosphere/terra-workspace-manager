package bio.terra.workspace.service.workspace.flight.create.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreateAzureContextFlightTest extends BaseAzureTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private SamService samService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private AzureTestConfiguration azureTestConfiguration;

  @Test
  void successCreatesProjectAndContext() throws Exception {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createInputParameters(workspaceId, userRequest),
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
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());
  }
}
