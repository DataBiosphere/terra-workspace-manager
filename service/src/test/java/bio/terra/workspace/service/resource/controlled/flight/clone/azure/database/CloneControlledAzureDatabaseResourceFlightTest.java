package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.CloneControlledAzureStorageContainerResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.ClonedAzureStorageContainer;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import bio.terra.workspace.service.workspace.model.Workspace;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CloneControlledAzureDatabaseResourceFlightTest extends BaseAzureConnectedTest {

    @Autowired private JobService jobService;
    @Autowired private AzureTestUtils azureTestUtils;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserAccessUtils userAccessUtils;

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
    void cloneControlledAzureDatabase_dummy()
        throws InterruptedException {

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
        inputs.put(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, azureTestUtils.getAzureCloudContext());
        inputs.put(WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, workspaceId);

        // TODO: populate these values
        inputs.put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
            UUID.fromString("< workspace ID that owns the blob storage container in which the dumpfile will be written >"));

        inputs.put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_NAME,
            "< name of db within the integration test MRG's database server that should be cloned >");

        inputs.put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_SOURCE_DATABASE_SERVER,
            "< integration test MRG postgres server name >");

        inputs.put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_USER,
            "< integration test MRG LZ managed identity >");

        inputs.put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_BLOB_CONTAINER_URL_AUTHENTICATED,
            "< blob storage container url with SAS token >");

        var result =
            StairwayTestUtils.blockUntilFlightCompletes(
                jobService.getStairway(),
                CloneControlledAzureDatabaseResourceFlight.class,
                inputs,
                Duration.ofMinutes(1),
                null);

        assertEquals(result.getFlightStatus(), FlightStatus.SUCCESS);
    }
}

