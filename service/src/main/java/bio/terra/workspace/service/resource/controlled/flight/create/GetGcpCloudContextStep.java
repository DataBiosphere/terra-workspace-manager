package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetGcpCloudContextStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetGcpCloudContextStep.class);
  private final UUID workspaceId;
  private final GcpCloudContextService gcpCloudContextService;
  private final AuthenticatedUserRequest userRequest;

  public GetGcpCloudContextStep(
      UUID workspaceId,
      GcpCloudContextService gcpCloudContextService,
      AuthenticatedUserRequest userRequest) {
    this.workspaceId = workspaceId;
    this.gcpCloudContextService = gcpCloudContextService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // The service does all of the work here, so this is a very simple step.
    // It is idempotent. Either the update to the database with proper policy names
    // will work, or it won't. So re-doing this step is fine.
    GcpCloudContext context =
        gcpCloudContextService.getRequiredGcpCloudContext(workspaceId, userRequest);
    flightContext.getWorkingMap().put(ControlledResourceKeys.GCP_CLOUD_CONTEXT, context);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
