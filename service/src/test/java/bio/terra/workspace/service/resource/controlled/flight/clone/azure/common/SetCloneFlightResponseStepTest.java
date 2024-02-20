package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("azureUnit")
class SetCloneFlightResponseStepTest extends BaseSpringBootAzureUnitTest {

  @Test
  void setCloneFlightResponse_doStep() throws InterruptedException {
    var flightContext = mock(FlightContext.class);
    var clonedResource =
        new ClonedAzureResource(
            CloningInstructions.COPY_RESOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            ControlledAzureResourceFixtures
                .makeDefaultControlledAzureManagedIdentityResourceBuilder(
                    ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters(),
                    UUID.randomUUID())
                .managedIdentityName("idfoobar")
                .build());
    var workingMap = new FlightMap();
    workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE, clonedResource);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    var result = new SetCloneFlightResponseStep().doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    assertEquals(
        clonedResource,
        workingMap.get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class));
    assertEquals(
        HttpStatus.OK, workingMap.get(JobMapKeys.STATUS_CODE.getKeyName(), HttpStatus.class));
  }

  @Test
  void setCloneFlightResponse_doEmptyStep() throws InterruptedException {
    var flightContext = mock(FlightContext.class);
    var workingMap = new FlightMap();
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    var result = new SetCloneFlightResponseStep().doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    assertNull(workingMap.get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class));
    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        workingMap.get(JobMapKeys.STATUS_CODE.getKeyName(), HttpStatus.class));
  }
}
