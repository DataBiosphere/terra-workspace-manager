package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateGoogleContextRBSFlightTest extends BaseConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(5);

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private JobService jobService;

  @Test
  public void successRBSCreatesProjectAndContext() throws Exception {
    // NOTE: At the moment, this test is just a skeleton to ensure we can connect to RBS.
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest testUser =
        new AuthenticatedUserRequest()
            .subjectId("RBSUnit")
            .email("rbs@unit.com")
            .token(Optional.of("not-a-real-token"));

    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    FlightMap inputs = new FlightMap();
    // inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), testUser);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGoogleContextRBSFlight.class,
            inputs,
            STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
  }

  private UUID createWorkspace() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    return workspace.workspaceId();
  }
}
