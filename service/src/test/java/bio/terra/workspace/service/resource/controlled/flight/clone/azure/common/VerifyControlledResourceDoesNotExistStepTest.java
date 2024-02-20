package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ValidationException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class VerifyControlledResourceDoesNotExistStepTest extends BaseSpringBootAzureUnitTest {

  @Test
  void doStep_resourceDoesNotExist() throws InterruptedException {
    var resourceDao = mock(ResourceDao.class);
    var flightContext = mock(FlightContext.class);
    var inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.randomUUID());
    inputParameters.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, UUID.randomUUID());
    when(flightContext.getInputParameters()).thenReturn(inputParameters);

    var result = new VerifyControlledResourceDoesNotExist(resourceDao).doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
  }

  @Test
  void doStep_resourceAlreadyExists() throws InterruptedException {
    var resourceDao = mock(ResourceDao.class);
    var flightContext = mock(FlightContext.class);
    var inputParameters = new FlightMap();

    var destinationWorkspaceId = UUID.randomUUID();
    var destinationContainerName = "fake-container";
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    inputParameters.put(
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, destinationContainerName);
    when(flightContext.getInputParameters()).thenReturn(inputParameters);
    when(resourceDao.resourceExists(destinationWorkspaceId, destinationContainerName))
        .thenReturn(true);

    var result = new VerifyControlledResourceDoesNotExist(resourceDao).doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, result.getStepStatus());
    assertEquals(ValidationException.class, result.getException().get().getClass());
  }
}
