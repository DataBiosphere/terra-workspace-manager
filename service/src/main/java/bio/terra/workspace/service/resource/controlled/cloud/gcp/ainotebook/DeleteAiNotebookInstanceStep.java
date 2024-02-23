package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.Operation;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled AI Platform notebook instance. */
public class DeleteAiNotebookInstanceStep implements DeleteControlledResourceStep {
  private final Logger logger = LoggerFactory.getLogger(DeleteAiNotebookInstanceStep.class);

  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;

  public DeleteAiNotebookInstanceStep(
      ControlledAiNotebookInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    InstanceName instanceName = resource.toInstanceName();
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      Optional<Operation> rawOperation = deleteIfFound(instanceName, notebooks);
      if (rawOperation.isEmpty()) {
        logger.info("Notebook instance {} already deleted", instanceName.formatName());
        return StepResult.getStepResultSuccess();
      }
      GcpUtils.pollAndRetry(
          notebooks.operations().operationCow(rawOperation.get()),
          Duration.ofSeconds(20),
          Duration.ofMinutes(10));
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
