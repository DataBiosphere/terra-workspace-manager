package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.mockito.Mock;

@Tag("azureUnit")
public class CloneControlledAzureDatabaseResourceFlightTest extends BaseAzureUnitTest {

  @Mock private FlightBeanBag flightBeanBag;
  @Mock private AuthenticatedUserRequest userRequest;

  static UUID sourceWorkspaceId = UUID.randomUUID();
  static UUID destinationWorkspaceId = UUID.randomUUID();
  static UUID destinationResourceId = UUID.randomUUID();

  CloneControlledAzureDatabaseResourceFlight createFlight() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters("idowner", false);

    var sourceResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, sourceWorkspaceId, CloningInstructions.COPY_NOTHING)
            .build();

    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceResource);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        CloningInstructions.COPY_NOTHING.name());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, sourceResource.getName());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);

    return new CloneControlledAzureDatabaseResourceFlight(inputs, flightBeanBag);
  }

  @Test
  void cloneControlledAzureDatabase_copyDefinitionSteps() {
    var flight = createFlight();
    var copyDefinitionSteps = flight.copyDefinition(flightBeanBag, flight.getInputParameters());
    assertEquals(1, copyDefinitionSteps.size());
    assertEquals(
        List.of(CopyControlledAzureDatabaseDefinitionStep.class),
        copyDefinitionSteps.stream()
            .map(pair -> pair.step().getClass())
            .collect(Collectors.toList()));
  }

  @Test
  void cloneControlledAzureDatabase_copyResourceSteps() {
    var flight = createFlight();
    var copyResourceSteps = flight.copyResource(flightBeanBag, flight.getInputParameters());
    assertEquals(4, copyResourceSteps.size());
    assertEquals(
        List.of(CreateAzureStorageContainerStep.class, DumpAzureDatabaseStep.class, RestoreAzureDatabaseStep.class, DeleteAzureStorageContainerStep.class),
        copyResourceSteps.stream()
            .map(pair -> pair.step().getClass())
            .collect(Collectors.toList()));
  }
}
