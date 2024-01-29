package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.springframework.http.HttpStatus;

public class SetCloneFlightResponseStep implements Step {
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var result =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE,
                ClonedAzureResource.class);

    if (result == null) {
      FlightUtils.setResponse(context, null, HttpStatus.INTERNAL_SERVER_ERROR);
    } else {
      FlightUtils.setResponse(context, result, HttpStatus.OK);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
