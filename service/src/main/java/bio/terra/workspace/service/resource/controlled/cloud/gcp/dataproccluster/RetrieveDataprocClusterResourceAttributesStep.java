package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiControlledDataprocClusterUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.model.Cluster;
import java.io.IOException;
import org.springframework.http.HttpStatus;

public class RetrieveDataprocClusterResourceAttributesStep implements Step {

  private final ControlledDataprocClusterResource resource;
  private final CrlService crlService;

  public RetrieveDataprocClusterResourceAttributesStep(
      ControlledDataprocClusterResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    ClusterName clusterName = resource.toClusterName();
    try {
      Cluster cluster = crlService.getDataprocCow().clusters().get(clusterName).execute();
      ApiControlledDataprocClusterUpdateParameters existingUpdateParameters =
          new ApiControlledDataprocClusterUpdateParameters();

      if (cluster.getConfig().getWorkerConfig() != null) {
        existingUpdateParameters.setNumPrimaryWorkers(
            cluster.getConfig().getWorkerConfig().getNumInstances());
      }
      if (cluster.getConfig().getSecondaryWorkerConfig() != null) {
        existingUpdateParameters.setNumSecondaryWorkers(
            cluster.getConfig().getSecondaryWorkerConfig().getNumInstances());
      }
      if (cluster.getConfig().getAutoscalingConfig() != null) {
        existingUpdateParameters.setAutoscalingPolicy(
            cluster.getConfig().getAutoscalingConfig().getPolicyUri());
      }
      // GracefulDecommissionTimeout is a property on the autoscaling policy, not the cluster.

      // Due to the irreversible nature of lifecycleConfig, we do not track its value. Cannot add
      // lifecycle to clusters without it. So if we remove a cluster's lifecycleConfig, we cannot
      // add it back.

      workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, existingUpdateParameters);
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
