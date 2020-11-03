package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteGoogleContextFlightTest extends BaseConnectedTest {
  /**
   * How long to wait for a delete context Stairway flight to complete before timing out the test.
   */
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(2);
  /**
   * How long to wait for a create context Stairway flight to complete before timing out the test.
   */
  private static final Duration CREATION_FLIGHT_TIMEOUT = Duration.ofMinutes(1);

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CloudResourceManagerCow resourceManager;
  @Autowired private JobService jobService;

  @Test
  public void deleteContext() throws Exception {
    UUID workspaceId = createWorkspace();

    // Both creating and deleting the google context happen to only need the workspace id as input.
    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGoogleContextFlight.class,
            inputParameters,
            CREATION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId = workspaceDao.getCloudContext(workspaceId).googleProjectId().get();
    Project project = resourceManager.projects().get(projectId).execute();
    assertEquals("ACTIVE", project.getLifecycleState());

    // Delete the google context.
    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGoogleContextFlight.class,
            inputParameters,
            DELETION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    project = resourceManager.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  @Test
  public void deleteNonExistentContextIsOk() throws Exception {
    UUID workspaceId = createWorkspace();
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGoogleContextFlight.class,
            inputParameters,
            DELETION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  /** Creates a workspace, returning its workspaceId. */
  // TODO make it easier for tests to create workspaces using WorkspaceService.
  private UUID createWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    workspaceDao.createWorkspace(workspaceId, /* spendProfile= */ null);
    return workspaceId;
  }
}
