package bio.terra.workspace.service.workspace.flight.create.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
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
  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  void successCreatesContext() throws Exception {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    String jobId = UUID.randomUUID().toString();
    workspaceService.createAzureCloudContext(
        workspaceId,
        jobId,
        userRequest,
        /* resultPath */ null,
        azureTestUtils.getAzureCloudContext());

    // Wait for the job to complete
    FlightState flightState =
        StairwayTestUtils.pollUntilComplete(
            jobId, jobService.getStairway(), Duration.ofSeconds(30), STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Flight should have created a cloud context.
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());
    AzureCloudContext azureCloudContext =
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).get();
    assertEquals(azureCloudContext, azureTestUtils.getAzureCloudContext());
  }

  @Test
  void errorRevertsChanges() throws Exception {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    // There should be no cloud context created.
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isEmpty());
  }
}
