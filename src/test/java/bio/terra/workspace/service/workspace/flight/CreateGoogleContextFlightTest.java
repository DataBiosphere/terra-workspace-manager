package bio.terra.workspace.service.workspace.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.serviceusage.v1.model.GoogleApiServiceusageV1Service;
import com.google.api.services.serviceusage.v1.model.ListServicesResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateGoogleContextFlightTest extends BaseConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(5);

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CloudResourceManagerCow resourceManager;
  @Autowired private ServiceUsageCow serviceUsage;
  @Autowired private CloudBillingClientCow billingClient;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;

  @Test
  public void successCreatesProjectAndContext() throws Exception {
    UUID workspaceId = createWorkspace();
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGoogleContextFlight.class,
            createInputParameters(workspaceId, spendUtils.defaultBillingAccountId()),
            STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId =
        flightState
            .getResultMap()
            .get()
            .get(WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID, String.class);
    assertEquals(
        WorkspaceCloudContext.createGoogleContext(projectId),
        workspaceDao.getCloudContext(workspaceId));
    Project project = resourceManager.projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertServiceApisEnabled(project, CreateProjectStep.ENABLED_SERVICES);
    assertEquals(
        "billingAccounts/" + spendUtils.defaultBillingAccountId(),
        billingClient.getProjectBillingInfo("projects/" + projectId).getBillingAccountName());
  }

  @Test
  public void errorRevertsChanges() throws Exception {
    UUID workspaceId = createWorkspace();
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));

    // Submit a flight class that always errors.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            ErrorCreateGoogleContextFlight.class,
            createInputParameters(workspaceId, spendUtils.defaultBillingAccountId()),
            STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    String projectId =
        flightState
            .getResultMap()
            .get()
            .get(WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID, String.class);
    // The Project should exist, but requested to be deleted.
    Project project = resourceManager.projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
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

  /** Create the FlightMap input parameters required for the {@link CreateGoogleContextFlight}. */
  private static FlightMap createInputParameters(UUID workspaceId, String billingAccountId) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    inputs.put(WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, billingAccountId);
    return inputs;
  }

  private void assertServiceApisEnabled(Project project, List<String> enabledApis)
      throws Exception {
    List<String> serviceNames =
        enabledApis.stream()
            .map(
                apiName ->
                    String.format("projects/%d/services/%s", project.getProjectNumber(), apiName))
            .collect(Collectors.toList());
    ListServicesResponse servicesList =
        serviceUsage
            .services()
            .list("projects/" + project.getProjectId())
            .setFilter("state:ENABLED")
            .execute();
    assertThat(
        servicesList.getServices().stream()
            .map(GoogleApiServiceusageV1Service::getName)
            .collect(Collectors.toList()),
        Matchers.hasItems(serviceNames.toArray()));
  }

  /**
   * An extension of {@link CreateGoogleContextFlight} that has the last step as an error, causing
   * the flight to always attempt to be rolled back.
   */
  public static class ErrorCreateGoogleContextFlight extends CreateGoogleContextFlight {
    public ErrorCreateGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new StairwayTestUtils.ErrorDoStep());
    }
  }
}
