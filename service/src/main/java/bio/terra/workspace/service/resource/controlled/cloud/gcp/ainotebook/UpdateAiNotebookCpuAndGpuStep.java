package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_FACTOR_INCREASE;
import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_SLEEP_DURATION;
import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_SLEEP_DURATION_MAX;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_MACHINE_TYPE;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.cloud.notebooks.v1.Instance;
import com.google.cloud.notebooks.v1.NotebookServiceClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Update the CPU/GPU using the client library for AI notebooks {@link NotebookServiceClient}. */
public class UpdateAiNotebookCpuAndGpuStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;

  public UpdateAiNotebookCpuAndGpuStep(ControlledAiNotebookInstanceResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // The effective update instructions will respectively be null if no update would occur
    // (i.e., the previous attribute and the requested new attribute value are equal)
    String effectiveMachineType =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE, String.class);

    AcceleratorConfig effectiveAcceleratorConfig =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG,
                AcceleratorConfig.class);

    return updateAiNotebookCpuAndGpu(effectiveMachineType, effectiveAcceleratorConfig);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    String effectiveMachineType =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE, String.class);
    AcceleratorConfig effectiveAcceleratorConfig =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG,
            AcceleratorConfig.class);
    // If no effective update was requested, then there's nothing to undo.
    if (effectiveMachineType == null && effectiveAcceleratorConfig == null) {
      return StepResult.getStepResultSuccess();
    }
    String previousMachineType = workingMap.get(PREVIOUS_MACHINE_TYPE, String.class);
    AcceleratorConfig previousAcceleratorConfig =
        workingMap.get(PREVIOUS_ACCELERATOR_CONFIG, AcceleratorConfig.class);
    // Attempt to revert cloud update (if it happened).
    return updateAiNotebookCpuAndGpu(previousMachineType, previousAcceleratorConfig);
  }

  private StepResult updateAiNotebookCpuAndGpu(
      String effectiveMachineType, AcceleratorConfig effectiveAcceleratorConfig) {
    if (effectiveMachineType == null && effectiveAcceleratorConfig == null) {
      return StepResult.getStepResultSuccess();
    }
    String projectId = resource.getProjectId();

    try (NotebookServiceClient notebookServiceClient = NotebookServiceClient.create()) {
      InstanceName instanceName = resource.toInstanceName(projectId);

      List<Class<? extends Exception>> retryableErrors = new ArrayList<>();
      // Exceptions include waiting to queue the operation (409 conflict).
      retryableErrors.add(GoogleJsonResponseException.class);

      if (effectiveMachineType != null) {
        RetryUtils.getWithRetryOnException(
            () ->
                notebookServiceClient
                    .setInstanceMachineTypeAsync(
                        com.google.cloud.notebooks.v1.SetInstanceMachineTypeRequest.newBuilder()
                            .setName(instanceName.formatName())
                            .setMachineType(effectiveMachineType)
                            .build())
                    .get(),
            Duration.ofMinutes(7),
            DEFAULT_RETRY_SLEEP_DURATION,
            DEFAULT_RETRY_FACTOR_INCREASE,
            DEFAULT_RETRY_SLEEP_DURATION_MAX,
            retryableErrors);
      }

      if (effectiveAcceleratorConfig != null) {
        RetryUtils.getWithRetryOnException(
            () ->
                notebookServiceClient
                    .setInstanceAcceleratorAsync(
                        com.google.cloud.notebooks.v1.SetInstanceAcceleratorRequest.newBuilder()
                            .setName(instanceName.formatName())
                            .setCoreCount(effectiveAcceleratorConfig.coreCount())
                            .setType(
                                Instance.AcceleratorType.valueOf(effectiveAcceleratorConfig.type()))
                            .build())
                    .get(),
            Duration.ofMinutes(7),
            DEFAULT_RETRY_SLEEP_DURATION,
            DEFAULT_RETRY_FACTOR_INCREASE,
            DEFAULT_RETRY_SLEEP_DURATION_MAX,
            retryableErrors);
      }

    } catch (ExecutionException | InterruptedException e) {
      // All retries are handled above. Do not retry here.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return StepResult.getStepResultSuccess();
  }
}
