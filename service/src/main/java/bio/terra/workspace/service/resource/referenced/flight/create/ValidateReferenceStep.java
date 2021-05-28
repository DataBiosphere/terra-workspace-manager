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
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ReferencedResource.class);
    AuthenticatedUserRequest userReq =
        inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    referencedResource.validateReference(beanBag, userReq);
    return StepResult.getStepResultSuccess();
  }

  // This is a read-only step, so there's nothing to undo.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
