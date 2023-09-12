package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.GcpFlightExceptionUtils;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.model.Cluster;
import java.io.IOException;

public class ValidateDataprocClusterUpdateStep implements Step {
  private final ControlledDataprocClusterResource resource;
  private final CrlService crlService;

  private static final String CLUSTER_RUNNING_STATE = "RUNNING";

  public ValidateDataprocClusterUpdateStep(
      ControlledDataprocClusterResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  // Ensure that the cluster is in a 'RUNNING' state before updating attributes
  // Throw a conflict exception otherwise
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    try {
      Cluster cluster =
          crlService.getDataprocCow().clusters().get(resource.toClusterName()).execute();
      if (cluster.getStatus().getState() != null
          && !cluster.getStatus().getState().equals(CLUSTER_RUNNING_STATE)) {
        throw new ConflictException(
            String.format(
                "Cluster must be in a RUNNING state to update attributes. Current cluster state is: %s",
                cluster.getStatus().getState()));
      }
    } catch (GoogleJsonResponseException e) {
      // Throw bad request exception for malformed parameters
      GcpFlightExceptionUtils.handleGcpBadRequestException(e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
