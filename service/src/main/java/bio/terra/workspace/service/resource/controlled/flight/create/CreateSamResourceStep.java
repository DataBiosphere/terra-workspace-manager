package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateSamResourceStep implements Step {

  private final SamService samService;
  private final ControlledResource resource;
  private final ControlledResourceIamRole privateResourceIamRole;
  private final String assignedUserEmail;
  private final AuthenticatedUserRequest userRequest;
  private final WsmApplicationService applicationService;
  private final Logger logger = LoggerFactory.getLogger(CreateSamResourceStep.class);

  public CreateSamResourceStep(
      SamService samService,
      ControlledResource resource,
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable String assignedUserEmail,
      WsmApplicationService applicationService,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.resource = resource;
    this.privateResourceIamRole = privateResourceIamRole;
    this.assignedUserEmail = assignedUserEmail;
    this.userRequest = userRequest;
    this.applicationService = applicationService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    if (resource.getManagedBy().equals(ManagedByType.MANAGED_BY_APPLICATION)) {
      WsmWorkspaceApplication app =
          applicationService.getWorkspaceApplication(
              resource.getWorkspaceId(), resource.getApplicationId());

      samService.createControlledResource(
          resource,
          privateResourceIamRole,
          assignedUserEmail,
          app.getApplication().getServiceAccount(),
          userRequest);
    } else {
      samService.createControlledResource(
          resource, privateResourceIamRole, assignedUserEmail, null, userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    samService.deleteControlledResource(resource, userRequest);
    return StepResult.getStepResultSuccess();
  }
}
