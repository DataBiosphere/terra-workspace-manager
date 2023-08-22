package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiControlledDataprocClusterUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterLifecycleConfig;
import bio.terra.workspace.service.crl.CrlService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.model.AutoscalingConfig;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.ClusterConfig;
import com.google.api.services.dataproc.model.InstanceGroupConfig;
import com.google.api.services.dataproc.model.LifecycleConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

public class UpdateDataprocClusterStep implements Step {

  private final ControlledDataprocClusterResource resource;
  private final CrlService crlService;

  // Mask paths for cluster config fields that are updatable
  private static final String NUM_WORKERS = "config.worker_config.num_instances";
  private static final String NUM_SECONDARY_WORKERS =
      "config.secondary_worker_config.num_instances";
  private static final String AUTOSCALING_POLICY = "config.autoscaling_config.policy_uri";
  // Only clusters created with scheduled deletion enabled can be updated. Removing scheduled
  // deletion from a cluster, i.e. settling lifecycleConfig to be empty is irreversible.
  private static final String IDLE_DELETE_TTL = "config.lifecycle_config.idle_delete_ttl";
  private static final String AUTO_DELETE_TTL = "config.lifecycle_config.auto_delete_ttl";
  private static final String AUTO_DELETE_TIME = "config.lifecycle_config.auto_delete_time";

  public UpdateDataprocClusterStep(
      ControlledDataprocClusterResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(inputParameters, UPDATE_PARAMETERS);
    ApiControlledDataprocClusterUpdateParameters updateParameters =
        inputParameters.get(UPDATE_PARAMETERS, ApiControlledDataprocClusterUpdateParameters.class);

    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    return updateCluster(updateParameters);
  }

  // Restore the previous values of the update parameters
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap workingMap = flightContext.getWorkingMap();
    ApiControlledDataprocClusterUpdateParameters prevParameters =
        FlightUtils.getRequired(
            workingMap,
            PREVIOUS_UPDATE_PARAMETERS,
            ApiControlledDataprocClusterUpdateParameters.class);
    return updateCluster(prevParameters);
  }

  // Update the cluster with the given update parameters.
  private StepResult updateCluster(ApiControlledDataprocClusterUpdateParameters updateParameters) {
    ClusterName clusterName = resource.toClusterName();
    List<String> updateMaskPaths = new ArrayList<>();
    // Contains only the values that are being updated, the rest are null
    Cluster updatedCluster =
        new Cluster().setClusterName(clusterName.name()).setConfig(new ClusterConfig());

    // Apply the update parameters to the cluster config mask
    if (updateParameters.getNumPrimaryWorkers() != null) {
      updateMaskPaths.add(NUM_WORKERS);
      updatedCluster
          .getConfig()
          .setWorkerConfig(
              new InstanceGroupConfig().setNumInstances(updateParameters.getNumPrimaryWorkers()));
    }
    if (updateParameters.getNumSecondaryWorkers() != null) {
      updateMaskPaths.add(NUM_SECONDARY_WORKERS);
      updatedCluster
          .getConfig()
          .setSecondaryWorkerConfig(
              new InstanceGroupConfig().setNumInstances(updateParameters.getNumSecondaryWorkers()));
    }
    if (updateParameters.getAutoscalingPolicy() != null) {
      updateMaskPaths.add(AUTOSCALING_POLICY);
      updatedCluster
          .getConfig()
          .setAutoscalingConfig(
              new AutoscalingConfig().setPolicyUri(updateParameters.getAutoscalingPolicy()));
    }
    ApiGcpDataprocClusterLifecycleConfig lifecycleConfig = updateParameters.getLifecycleConfig();
    if (lifecycleConfig != null) {
      updatedCluster.getConfig().setLifecycleConfig(new LifecycleConfig());
      if (lifecycleConfig.getIdleDeleteTtl() != null) {
        updateMaskPaths.add(IDLE_DELETE_TTL);
        updatedCluster
            .getConfig()
            .getLifecycleConfig()
            .setIdleDeleteTtl(updateParameters.getLifecycleConfig().getIdleDeleteTtl());
      }
      // Only one autoDeleteTtl and autoDeleteTime can be set (already validated in controller)
      if (lifecycleConfig.getAutoDeleteTtl() != null) {
        updateMaskPaths.add(AUTO_DELETE_TTL);
        updatedCluster
            .getConfig()
            .getLifecycleConfig()
            .setAutoDeleteTtl(updateParameters.getLifecycleConfig().getAutoDeleteTtl());
      } else if (lifecycleConfig.getAutoDeleteTime() != null) {
        updateMaskPaths.add(AUTO_DELETE_TIME);
        updatedCluster
            .getConfig()
            .getLifecycleConfig()
            .setAutoDeleteTime(
                updateParameters.getLifecycleConfig().getAutoDeleteTime().toString());
      }
    }
    try {
      crlService
          .getDataprocCow()
          .clusters()
          .patch(
              clusterName,
              updatedCluster,
              String.join(",", updateMaskPaths),
              updateParameters.getGracefulDecommissionTimeout())
          .execute();
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
}
