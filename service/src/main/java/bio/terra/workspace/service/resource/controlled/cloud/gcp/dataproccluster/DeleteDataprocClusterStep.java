package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ResourceIsDeletedException;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.model.Operation;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled Dataproc cluster. */
public class DeleteDataprocClusterStep implements DeleteControlledResourceStep {
  private final Logger logger = LoggerFactory.getLogger(DeleteDataprocClusterStep.class);

  private final ControlledDataprocClusterResource resource;
  private final CrlService crlService;

  public DeleteDataprocClusterStep(
      ControlledDataprocClusterResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    ClusterName clusterName = resource.toClusterName();
    DataprocCow dataprocCow = crlService.getDataprocCow();
    try {
      Optional<Operation> rawOperation = deleteIfFound(clusterName, dataprocCow);
      if (rawOperation.isEmpty()) {
        logger.info("Dataproc cluster {} already deleted", clusterName.formatName());
        return StepResult.getStepResultSuccess();
      }
      GcpUtils.pollAndRetry(
          dataprocCow.regionOperations().operationCow(rawOperation.get()),
          Duration.ofSeconds(20),
          Duration.ofMinutes(10));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Starts the deletion of cluster. If the cluster is not found, returns empty. Otherwise, returns
   * the delete operation.
   */
  private Optional<Operation> deleteIfFound(ClusterName clusterName, DataprocCow dataprocCow)
      throws IOException {
    try {
      return Optional.of(dataprocCow.clusters().delete(clusterName).execute());
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
    throw new ResourceIsDeletedException(
        String.format(
            "Cannot undo delete of GCS Dataproc cluster %s in workspace %s.",
            resource.getResourceId(), resource.getWorkspaceId()));
  }
}
