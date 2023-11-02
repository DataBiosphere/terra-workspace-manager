package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
public class CloneControlledAzureDatabaseResourceFlightTest extends BaseAzureConnectedTest {

    @Autowired private JobService jobService;
    @Autowired private AzureTestUtils azureTestUtils;
    @Autowired private WorkspaceService workspaceService;

    private final AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest().token(Optional.of("token"));

    @Test
    void cloneControlledAzureDatabase_dummy()
        throws InterruptedException {

        UUID workspaceId = UUID.randomUUID();

        var creationParameters =
            ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
                UUID.randomUUID().toString(), false);

        var resource =
            ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                    creationParameters, workspaceId)
                .build();

        FlightMap inputs = new FlightMap();
        inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, resource);

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

