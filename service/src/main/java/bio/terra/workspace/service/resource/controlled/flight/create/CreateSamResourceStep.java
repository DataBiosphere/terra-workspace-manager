package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateSamResourceStep implements Step {

  private final SamService samService;
  private final ControlledResource resource;
  private final ControlledResourceIamRole privateResourceIamRole;
  private final String assignedUserEmail;
  private final AuthenticatedUserRequest userRequest;

  private final Logger logger = LoggerFactory.getLogger(CreateSamResourceStep.class);

  public CreateSamResourceStep(
      SamService samService,
      ControlledResource resource,
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable String assignedUserEmail,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.resource = resource;
    this.privateResourceIamRole = privateResourceIamRole;
    this.assignedUserEmail = assignedUserEmail;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    samService.createControlledResource(
        resource, privateResourceIamRole, assignedUserEmail, userRequest);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    samService.deleteControlledResource(resource, userRequest);
    return StepResult.getStepResultSuccess();
  }
}
