package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateSamResourceStep implements Step {

  private final SamService samService;
  private final ControlledResource resource;
  private final List<ControlledResourceIamRole> privateResourceIamRole;
  private final AuthenticatedUserRequest userReq;

  private final Logger logger = LoggerFactory.getLogger(CreateSamResourceStep.class);

  public CreateSamResourceStep(
      SamService samService,
      ControlledResource resource,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.resource = resource;
    this.privateResourceIamRole = privateResourceIamRoles;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    try {
      samService.createControlledResource(resource, privateResourceIamRole, userReq);
    } catch (SamApiException samException) {
      // Do nothing if the resource to create already exists, this may not be the first time do is
      // called. Other exceptions still need to be surfaced.
      // Resource IDs are randomly generated, so we trust that this same flight must have created
      // an existing Sam resource.
      logger.debug(
          "Sam API error while doing CreateSamResourceStep, code is "
              + samException.getApiExceptionStatus());
      if (samException.getApiExceptionStatus() != HttpStatus.CONFLICT.value()) {
        throw samException;
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    try {
      samService.deleteControlledResource(resource, userReq);
    } catch (SamApiException samApiException) {
      // Do nothing if the resource to delete is not found, this may not be the first time undo is
      // called. Other exceptions still need to be surfaced.
      logger.debug(
          "Sam API error while undoing CreateSamResourceStep, code is "
              + samApiException.getApiExceptionStatus());
      if (samApiException.getApiExceptionStatus() != HttpStatus.NOT_FOUND.value()) {
        throw samApiException;
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
