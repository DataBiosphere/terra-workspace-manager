package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;


public abstract class DeleteAzureControlledResourceStep implements DeleteControlledResourceStep {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    try {
      return deleteResource(context);
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      return handleResourceDeleteException(e, context);
    }
  }

  protected abstract StepResult deleteResource(FlightContext context) throws InterruptedException;

  /**
   * @param context the flight context, included so downstream implementations can access any parameters they need.
   */
  protected StepResult handleResourceDeleteException(Exception e, FlightContext context) {
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
  }

}
