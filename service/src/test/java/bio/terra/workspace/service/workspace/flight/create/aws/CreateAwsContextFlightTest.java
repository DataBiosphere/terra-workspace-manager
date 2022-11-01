package bio.terra.workspace.service.workspace.flight.create.aws;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.common.utils.BaseAwsConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateAwsContextFlightTest extends BaseAwsConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private AwsTestUtils awsTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  void successCreatesContext() throws Exception {
    Workspace workspace = awsTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isEmpty());

    String jobId = UUID.randomUUID().toString();
    workspaceService.createAwsCloudContext(
            workspace,
            jobId,
            userRequest,
            /* resultPath */ null);

    // Wait for the job to complete
    FlightState flightState =
            StairwayTestUtils.pollUntilComplete(
                    jobId, jobService.getStairway(), Duration.ofSeconds(30), STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Flight should have created a cloud context.
    assertTrue(
            awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).isPresent());
    AwsCloudContext awsCloudContext =
            awsCloudContextService.getAwsCloudContext(workspace.getWorkspaceId()).get();
  }
}
