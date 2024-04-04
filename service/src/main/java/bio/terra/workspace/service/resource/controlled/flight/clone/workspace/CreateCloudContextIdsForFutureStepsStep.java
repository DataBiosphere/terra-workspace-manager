package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

/**
 * Generate flight IDs ahead of the steps that need them. The steps can then check the existence of
 * the flights or workspaces before creation to ensure idempotency.
 */
public class CreateCloudContextIdsForFutureStepsStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(
        ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID,
        context.getStairway().createFlightId());
    workingMap.put(
        ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID,
        context.getStairway().createFlightId());
    validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID,
        ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID);
    return StepResult.getStepResultSuccess();
  }

  // No side effects to undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
