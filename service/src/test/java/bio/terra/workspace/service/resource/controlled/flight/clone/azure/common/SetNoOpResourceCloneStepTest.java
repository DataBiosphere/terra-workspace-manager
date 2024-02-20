package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("azureUnit")
class SetNoOpResourceCloneStepTest extends BaseSpringBootAzureUnitTest {

  @Test
  void setNoOpResourceClone_doStep() throws InterruptedException {
    var flightContext = mock(FlightContext.class);
    var sourceResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters(),
                UUID.randomUUID())
            .managedIdentityName("idfoobar")
            .build();
    var expectedClone =
        new ClonedAzureResource(
            CloningInstructions.COPY_NOTHING,
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId());

    var workingMap = new FlightMap();
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    var result = new SetNoOpResourceCloneResourceStep(sourceResource).doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    assertEquals(
        expectedClone,
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE,
            ClonedAzureResource.class));
  }
}
