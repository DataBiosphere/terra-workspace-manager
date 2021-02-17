package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.WsmControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class GenerateControlledResourceIdStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final UUID resourceId = UUID.randomUUID();
    flightContext.getWorkingMap().put(ControlledResourceKeys.RESOURCE_ID, resourceId);

    // update the request object
    final WsmResource resource = flightContext.getInputParameters().get(
        JobMapKeys.REQUEST.getKeyName(), WsmResource.class);
    resource.setResourceId(resourceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // TODO: there's no way that I can see to remove an entry from a FlightMap.
    flightContext.getWorkingMap().put(ControlledResourceKeys.RESOURCE_ID, null);
    return StepResult.getStepResultSuccess();
  }
}
