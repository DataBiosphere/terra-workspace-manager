package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

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
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.cloud.notebooks.v1.Instance;
import com.google.cloud.notebooks.v1.NotebookServiceClient;
import java.time.Duration;
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

    var projectId = resource.getProjectId();
    return updateAiNotebookCpuAndGpu(projectId, effectiveMachineType, effectiveAcceleratorConfig);
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
    String projectId = resource.getProjectId();
    // Attempt to revert cloud update (if it happened).
    return updateAiNotebookCpuAndGpu(projectId, previousMachineType, previousAcceleratorConfig);
  }

  private StepResult updateAiNotebookCpuAndGpu(
      String projectId, String effectiveMachineType, AcceleratorConfig effectiveAcceleratorConfig) {
    if (effectiveMachineType == null && effectiveAcceleratorConfig == null) {
      return StepResult.getStepResultSuccess();
    }

    try (NotebookServiceClient notebookServiceClient = NotebookServiceClient.create()) {
      InstanceName instanceName = resource.toInstanceName(projectId);

      // TODO (aaronwa@):
      // The AI notebook may be stopped in the small window between the previous "check if notebook
      // stopped" step and when this update is executed. We should not retry in that case.
      // Catch this invalid state error (i.e., strictly not stopped - don't allow "stopping" here)
      // here somehow.

      //      List<Exception> nonRetryableErrors = new ArrayList<>();
      //      nonRetryableErrors.add()

      // DEBUGGING (edge case if the notebook is running here).
      //      notebookServiceClient
      //          .stopInstanceAsync(
      //              StopInstanceRequest.newBuilder().setName(instanceName.formatName()).build())
      //          .get();
      //      System.out.println("aaronwa: stopped notebook between check and update (edge case).");

      // TODO (aaronwa@): place these two into one combined update to simplify the undo process?
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
            Duration.ofMinutes(5),
            RetryUtils.DEFAULT_RETRY_SLEEP_DURATION,
            RetryUtils.DEFAULT_RETRY_FACTOR_INCREASE,
            RetryUtils.DEFAULT_RETRY_SLEEP_DURATION_MAX,
            null);
      }

      if (effectiveAcceleratorConfig != null) {
        RetryUtils.getWithRetryOnException(
            () ->
                notebookServiceClient
                    .setInstanceAcceleratorAsync(
                        com.google.cloud.notebooks.v1.SetInstanceAcceleratorRequest.newBuilder()
                            .setName(instanceName.formatName())
                            .setCoreCount(effectiveAcceleratorConfig.getCoreCount())
                            .setType(
                                Instance.AcceleratorType.valueOf(
                                    effectiveAcceleratorConfig.getType()))
                            .build())
                    .get());
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
