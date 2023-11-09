package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

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
public class CloneControlledAzureManagedIdentityResourceFlightTest extends BaseAzureUnitTest {

  @Mock private FlightBeanBag flightBeanBag;
  @Mock private AuthenticatedUserRequest userRequest;

  static UUID sourceWorkspaceId = UUID.randomUUID();
  static UUID destinationWorkspaceId = UUID.randomUUID();
  static UUID destinationResourceId = UUID.randomUUID();

  CloneControlledAzureManagedIdentityResourceFlight createFlight() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();

    var sourceResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, sourceWorkspaceId)
            .managedIdentityName("idfoobar")
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

    return new CloneControlledAzureManagedIdentityResourceFlight(inputs, flightBeanBag);
  }

  @Test
  void cloneControlledAzureManagedIdentity_copyDefinitionSteps() {
    var flight = createFlight();
    var copyDefinitionSteps = flight.copyDefinition(flightBeanBag, flight.getInputParameters());
    assertEquals(1, copyDefinitionSteps.size());
    assertEquals(
        List.of(CopyAzureManagedIdentityDefinitionStep.class),
        copyDefinitionSteps.stream()
            .map(pair -> pair.step().getClass())
            .collect(Collectors.toList()));
  }

  @Test
  void cloneControlledAzureManagedIdentity_copyResourceSteps() {
    var flight = createFlight();
    var copyResourceSteps = flight.copyResource(flightBeanBag, flight.getInputParameters());
    assertEquals(0, copyResourceSteps.size());
  }
}
