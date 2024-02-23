package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Operation;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled gce instance. */
public class DeleteGceInstanceStep implements DeleteControlledResourceStep {
  private final Logger logger = LoggerFactory.getLogger(DeleteGceInstanceStep.class);

  private final ControlledGceInstanceResource resource;
  private final CrlService crlService;

  public DeleteGceInstanceStep(ControlledGceInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    try {
      Optional<Operation> rawOperation =
          deleteIfFound(
              resource.getProjectId(),
              resource.getZone(),
              resource.getInstanceId(),
              cloudComputeCow);
      if (rawOperation.isEmpty()) {
        logger.info(
            "Compute instance projects/{}/zone/{}/instances/{} already deleted",
            resource.getProjectId(),
            resource.getZone(),
            resource.getInstanceId());
        return StepResult.getStepResultSuccess();
      }
      GcpUtils.pollAndRetry(
          cloudComputeCow
              .zoneOperations()
              .operationCow(resource.getProjectId(), resource.getZone(), rawOperation.get()),
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
      String projectId, String zone, String instanceId, CloudComputeCow cloudComputeCow)
      throws IOException {
    try {
      return Optional.of(cloudComputeCow.instances().delete(projectId, zone, instanceId).execute());
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
        "Cannot undo delete of GCE instance {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
