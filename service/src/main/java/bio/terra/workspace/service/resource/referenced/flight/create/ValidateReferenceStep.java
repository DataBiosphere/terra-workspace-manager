package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class ValidateReferenceStep implements Step {

  private final FlightBeanBag beanBag;

  public ValidateReferenceStep(FlightBeanBag beanBag) {
    this.beanBag = beanBag;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    ReferencedResource referencedResource =
        inputMap.get(ResourceKeys.RESOURCE, ReferencedResource.class);
    AuthenticatedUserRequest userRequest =
        inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    if (!referencedResource.checkAccess(beanBag, userRequest)) {
      throw new InvalidReferenceException(
          String.format(
              "Referenced resource %s was not found or you do not have access. Verify that your reference was correctly defined and that you have access.",
              referencedResource.getResourceId()));
    }
    return StepResult.getStepResultSuccess();
  }

  // This is a read-only step, so there's nothing to undo.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
