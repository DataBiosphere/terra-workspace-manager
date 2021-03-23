package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** A {@link Step} for pulling a project from a Buffer Service pool. */
public class PullProjectFromPoolStep implements Step {
  private final BufferService bufferService;
  private final CloudResourceManagerCow resourceManager;
  private final Logger logger = LoggerFactory.getLogger(PullProjectFromPoolStep.class);

  public PullProjectFromPoolStep(
      BufferService bufferService, CloudResourceManagerCow resourceManager) {
    this.bufferService = bufferService;
    this.resourceManager = resourceManager;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    try {
      String resourceId = flightContext.getWorkingMap().get(RBS_RESOURCE_ID, String.class);
      logger.info("Preparing to query Buffer Service for resource with ID: " + resourceId);
      HandoutRequestBody body = new HandoutRequestBody();
      body.setHandoutRequestId(resourceId);

      ResourceInfo info = bufferService.handoutResource(body);
      String projectId = info.getCloudResourceUid().getGoogleProjectUid().getProjectId();
      logger.info("Buffer Service returned project with id: " + projectId);
      flightContext.getWorkingMap().put(GCP_PROJECT_ID, projectId);
      return StepResult.getStepResultSuccess();
    } catch (BufferServiceAPIException e) {
      // The NOT_FOUND status code indicates that Buffer Service is still creating a project and we
      // must retry. Retrying TOO_MANY_REQUESTS gives the service time to recover from load.
      if (e.getStatusCode() == HttpStatus.NOT_FOUND
          || e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } catch (BufferServiceAuthorizationException e) {
      // If authorization fails, there is no recovering
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    if (projectId != null) {
      try {
        GcpUtils.deleteProject(projectId, resourceManager);
      } catch (IOException e) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
