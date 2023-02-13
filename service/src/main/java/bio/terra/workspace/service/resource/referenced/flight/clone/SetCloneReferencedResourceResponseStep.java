package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.springframework.http.HttpStatus;

public class SetCloneReferencedResourceResponseStep implements Step {
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    // put in the working map by CloneReferencedResourceStep
    ReferencedResource referencedResource =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE,
                ReferencedResource.class);
    FlightUtils.setResponse(flightContext, referencedResource, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo, only propagate the flight failure.
    return context.getResult();
  }
}
