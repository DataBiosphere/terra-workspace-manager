package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.GCP_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

/**
 * Retrieve the GCP cloud context, if applicable, and store it in the working map. Since this step
 * only reads data, it is idempotent
 */
public class GetGcpCloudContextStep implements Step {

  private final UUID workspaceUuid;
  private final GcpCloudContextService gcpCloudContextService;

  public GetGcpCloudContextStep(UUID workspaceUuid, GcpCloudContextService gcpCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    if (workingMap.get(GCP_CLOUD_CONTEXT, GcpCloudContext.class) == null) {
      final AuthenticatedUserRequest userRequest =
          FlightUtils.getRequired(
              flightContext.getInputParameters(),
              JobMapKeys.AUTH_USER_INFO.getKeyName(),
              AuthenticatedUserRequest.class);
      workingMap.put(
          GCP_CLOUD_CONTEXT,
          gcpCloudContextService.getRequiredGcpCloudContext(workspaceUuid, userRequest));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
