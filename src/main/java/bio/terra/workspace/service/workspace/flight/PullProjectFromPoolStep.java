package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.RBS_RESOURCE_ID;

import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.buffer.exception.BufferServiceAPIException;
import bio.terra.workspace.service.buffer.exception.BufferServiceAuthorizationException;
import java.io.IOException;

// NOTE: Do not use. This is just a test step to exercise RBS connection.
public class PullProjectFromPoolStep implements Step {
  private final BufferService bufferService;
  private final CloudResourceManagerCow resourceManager;

  public PullProjectFromPoolStep(
      BufferService bufferService, CloudResourceManagerCow resourceManager) {
    this.bufferService = bufferService;
    this.resourceManager = resourceManager;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    try {
      String resourceId = flightContext.getWorkingMap().get(RBS_RESOURCE_ID, String.class);
      HandoutRequestBody body = new HandoutRequestBody();
      body.setHandoutRequestId(resourceId);

      ResourceInfo info = bufferService.handoutResource(body);
      flightContext
          .getWorkingMap()
          .put(GOOGLE_PROJECT_ID, info.getCloudResourceUid().getGoogleProjectUid().getProjectId());
      return StepResult.getStepResultSuccess();
    } catch (BufferServiceAPIException e) {
      // Could be a transient error while RBS creates the project
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (BufferServiceAuthorizationException e) {
      // If authorization fails, there is no recovering
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    if (projectId != null) {
      try {
        GoogleUtils.deleteProject(projectId, resourceManager);
      } catch (IOException e) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
