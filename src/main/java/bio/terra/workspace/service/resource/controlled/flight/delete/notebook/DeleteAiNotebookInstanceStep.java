package bio.terra.workspace.service.resource.controlled.flight.delete.notebook;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.Operation;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled AI Platform notebook instance. */
public class DeleteAiNotebookInstanceStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteAiNotebookInstanceStep.class);

  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final WorkspaceService workspaceService;

  public DeleteAiNotebookInstanceStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      WorkspaceService workspaceService) {
    this.resource = resource;
    this.crlService = crlService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      Optional<Operation> rawOperation = deleteIfFound(instanceName, notebooks);
      if (rawOperation.isEmpty()) {
        logger.info("Notebook instance {} already deleted", instanceName.formatName());
        return StepResult.getStepResultSuccess();
      }
      OperationCow<Operation> operation =
          OperationUtils.pollUntilComplete(
              notebooks.operations().operationCow(rawOperation.get()),
              Duration.ofSeconds(20),
              Duration.ofMinutes(10));
      if (operation.getOperation().getError() != null) {
        throw new RetryException(
            String.format(
                "Error deleting notebook instance %s. %s",
                instanceName.formatName(), operation.getOperation().getError()));
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Starts the deletion of an instance. If the instance is not found, returns empty. Otherwise
   * returns the delete operation.
   */
  private Optional<Operation> deleteIfFound(
      InstanceName instanceName, AIPlatformNotebooksCow notebooks) throws IOException {
    try {
      return Optional.of(notebooks.instances().delete(instanceName).execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of GCS AI Platform Notebook instance {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
