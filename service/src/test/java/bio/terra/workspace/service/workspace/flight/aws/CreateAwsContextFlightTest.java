package bio.terra.workspace.service.workspace.flight.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.exception.RegionNotAllowedException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.aws.CreateAwsContextFlight;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("awsConnected")
class CreateAwsContextFlightTest extends BaseAwsConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;

  @MockBean private LandingZoneApiDispatch landingZoneApiDispatchMock;
  private final Region landingZoneRegion = Region.FRANCE_CENTRAL;

  @BeforeEach
  void setUp() {
    initSpendProfileMock();
    initLandingZoneApiDispatchMock();
  }

  @Test
  void successCreatesContext() throws Exception {
    Workspace workspace = awsTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();





    // There should be no cloud context initially.
    assertTrue(awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isEmpty());

    String jobId = UUID.randomUUID().toString();
    workspaceService.createAwsCloudContext(workspace, jobId, userRequest, /* resultPath */ null);

    // Wait for the job to complete
    FlightState flightState =
        StairwayTestUtils.pollUntilComplete(
            jobId, jobService.getStairway(), Duration.ofSeconds(1), STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Flight should have created a cloud context.
    assertTrue(
        awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isPresent());
    AwsCloudContext awsCloudContext =
        awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).get();
    assertEquals(awsCloudContext, awsTestUtils.getAwsCloudContext());
  }

  @Test
  void errorRevertsChanges() throws Exception {
    Workspace workspace = awsTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAwsContextFlight.class,
            awsTestUtils.createAwsContextInputParameters(
                workspace.getWorkspaceId(), userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    // There should be no cloud context created.
    assertTrue(awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isEmpty());
  }
}
