package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

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

public class VerifyResourceDoesNotExist implements Step {
  private static final Logger logger = LoggerFactory.getLogger(VerifyResourceDoesNotExist.class);
  private final ResourceDao resourceDao;
  private final String nameKey;

  public VerifyResourceDoesNotExist(ResourceDao resourceDao) {
    this(resourceDao, WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_NAME);
  }

  public VerifyResourceDoesNotExist(ResourceDao resourceDao, String nameKey) {
    this.resourceDao = resourceDao;
    this.nameKey = nameKey;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    var inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        nameKey);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destinationResourceName = inputParameters.get(nameKey, String.class);

    if (resourceDao.resourceExists(destinationWorkspaceId, destinationResourceName)) {
      logger.error("Resource already exists, name = {}", destinationResourceName);
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new ValidationException("Resource already exists"));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
