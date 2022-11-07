package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.common.exception.ValidationException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyContainerResourceDoesNotExist implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(VerifyContainerResourceDoesNotExist.class);
  private final ResourceDao resourceDao;

  public VerifyContainerResourceDoesNotExist(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    var inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_CONTAINER_NAME);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destinationResourceName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_CONTAINER_NAME, String.class);

    if (resourceDao.resourceExists(destinationWorkspaceId, destinationResourceName)) {
      logger.error("Storage container resource already exists, name = {}", destinationResourceName);
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ValidationException("Storage container resource already exists"));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
