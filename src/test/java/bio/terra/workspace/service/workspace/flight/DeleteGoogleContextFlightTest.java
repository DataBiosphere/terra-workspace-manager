package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.model.Workspace;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
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
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);
  /**
   * How long to wait for a create context Stairway flight to complete before timing out the test.
   */
  private static final Duration CREATION_FLIGHT_TIMEOUT = Duration.ofMinutes(5);

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CloudResourceManagerCow resourceManager;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;

  @Test
  public void deleteContext() throws Exception {
    UUID workspaceId = createWorkspace();

    // Both creating and deleting the google context happen to only need the workspace id as input.
    FlightMap createParameters = new FlightMap();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    createParameters.put(
        WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendUtils.defaultBillingAccountId());

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGoogleContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId = workspaceDao.getCloudContext(workspaceId).googleProjectId().get();
    Project project = resourceManager.projects().get(projectId).execute();
    assertEquals("ACTIVE", project.getLifecycleState());

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGoogleContextFlight.class,
            deleteParameters,
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
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    return workspace.workspaceId();
  }
}
