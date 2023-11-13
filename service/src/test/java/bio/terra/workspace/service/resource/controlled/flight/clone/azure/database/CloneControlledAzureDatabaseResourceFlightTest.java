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
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CloneControlledAzureDatabaseResourceFlightTest extends BaseAzureConnectedTest {

  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureDatabaseResourceFlightTest.class);

  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  private Workspace sourceWorkspace;
  private Workspace destinationWorkspace;

  @BeforeAll
  public void setup() throws InterruptedException {
    sourceWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
    destinationWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterAll
  public void cleanup() {
    // Deleting the workspace will also delete any resources contained in the workspace, including
    // VMs and the resources created during setup.
    workspaceService.deleteWorkspace(sourceWorkspace, userAccessUtils.defaultUserAuthRequest());
    workspaceService.deleteWorkspace(
        destinationWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  void cloneControlledAzureDatabaseFlightTest() throws InterruptedException {
    FlightMap inputs = new FlightMap();

    // To fill in this value:
    // Go to your workspace dashboard -> cloud information -> copy the "Storage SAS URL"
    inputs.put("BLOB_CONTAINER_URL_AUTHENTICATED", "https://...");

    // To fill in this value:
    // Go to your workspace dashboard -> cloud information -> copy the value starting with "sc-"
    //   from either the "Storage SAS URL" or the "Storage Container URL".
    // Or: use "sc-${WORKSPACE_ID}" (our conventional format for storage containers names)
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER_NAME, "sc-...");

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    var managedIdentityCreationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();

    // Set up a "source" managed identity
    var sourceManagedIdentityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                managedIdentityCreationParameters, sourceWorkspace.workspaceId())
            .managedIdentityName("idsource")
            .build();

    azureUtils.createResource(
        sourceWorkspace.workspaceId(),
        userRequest,
        sourceManagedIdentityResource,
        WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY,
        managedIdentityCreationParameters);

    // Set up a test "source" database
    var databaseName = "workflowclonetest";

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(databaseName, false);

    var sourceDatabaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, sourceWorkspace.workspaceId())
            .databaseOwner(sourceManagedIdentityResource.getWsmResourceFields().getName())
            .databaseName(databaseName)
            .build();

    azureUtils.createResource(
        sourceWorkspace.workspaceId(),
        userRequest,
        sourceDatabaseResource,
        WsmResourceType.CONTROLLED_AZURE_DATABASE,
        creationParameters);

    // Set up a destination managed identity,
    // simulating the result of the "clone managed identity" flight.
    var destinationManagedIdentityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                managedIdentityCreationParameters, destinationWorkspace.workspaceId())
            .managedIdentityName("iddestination")
            .build();

    azureUtils.createResource(
        destinationWorkspace.workspaceId(),
        userRequest,
        destinationManagedIdentityResource,
        WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY,
        managedIdentityCreationParameters);

    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceDatabaseResource);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    inputs.put(
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        CloningInstructions.COPY_RESOURCE.name());

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.randomUUID());

    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, databaseName);

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspace.workspaceId());

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_MANAGED_IDENTITY,
        destinationManagedIdentityResource.getWsmResourceFields().getName());

    var result =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CloneControlledAzureDatabaseResourceFlight.class,
            inputs,
            Duration.ofMinutes(1),
            null);

    assertEquals(FlightStatus.SUCCESS, result.getFlightStatus());

    ClonedAzureResource resultDatabase =
        result
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class);
  }
}
