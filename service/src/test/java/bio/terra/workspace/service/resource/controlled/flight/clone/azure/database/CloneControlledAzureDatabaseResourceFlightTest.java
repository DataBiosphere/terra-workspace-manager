package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CloneControlledAzureDatabaseResourceFlightTest extends BaseAzureConnectedTest {

  // Manual values used in flight test
  private final String destinationWorkspaceId = "< fill me in >";
  private final String sourceDbName = "workflowcloningtest";
  private final String dbServerName = "< fill in value from mrg-terra-integration-test-20211118>";
  private final String dbUserName = "< fill in value from mrg-terra-integration-test-20211118 >";
  private final String blobContainerUrlAuthenticated =
      "< fill in value from destination workspace file browser >";

  // Manual values used in azureDatabaseUtils function tests
  private final String blobFileName = "mypgdumpfile.tar";
  private final String blobContainerName = "sc-" + destinationWorkspaceId;
  private final String targetDbName = "workflowcloningtarget";

  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  private Workspace sharedWorkspace;
  private UUID workspaceId;

  @BeforeAll
  public void setup() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    sharedWorkspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
    workspaceId = sharedWorkspace.getWorkspaceId();
  }

  @AfterAll
  public void cleanup() {
    // Deleting the workspace will also delete any resources contained in the workspace, including
    // VMs and the resources created during setup.
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  void cloneControlledAzureDatabaseFlightTest() throws InterruptedException {

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID().toString(), false);

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId)
            .build();

    FlightMap inputs = new FlightMap();

    // defined by test setup
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, resource);
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
        azureTestUtils.getAzureCloudContext());
    inputs.put(WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, workspaceId);

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        UUID.fromString(destinationWorkspaceId));

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_NAME, sourceDbName);

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_SERVER, dbServerName);

    inputs.put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_USER, dbUserName);

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_BLOB_CONTAINER_URL_AUTHENTICATED,
        blobContainerUrlAuthenticated);

    var result =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CloneControlledAzureDatabaseResourceFlight.class,
            inputs,
            Duration.ofMinutes(1),
            null);

    assertEquals(result.getFlightStatus(), FlightStatus.SUCCESS);
  }

  @Test
  public void createDbDummyTest() throws InterruptedException {
    azureDatabaseUtilsRunner.createDatabaseWithDbRole(
        azureTestUtils.getAzureCloudContext(), workspaceId, "createdb-test-pod", targetDbName);
  }

  @Test
  public void pgDumpDatabaseTest() throws InterruptedException {

    azureDatabaseUtilsRunner.pgDumpDatabase(
        azureTestUtils.getAzureCloudContext(),
        workspaceId,
        "pgdump-test-pod",
        sourceDbName,
        dbServerName,
        dbUserName,
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated);
  }

  @Test
  public void pgRestoreDatabaseTest() throws InterruptedException {
    azureDatabaseUtilsRunner.pgRestoreDatabase(
        azureTestUtils.getAzureCloudContext(),
        workspaceId,
        "pgrestore-test-pod",
        targetDbName,
        dbServerName,
        dbUserName,
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated);
  }
}
