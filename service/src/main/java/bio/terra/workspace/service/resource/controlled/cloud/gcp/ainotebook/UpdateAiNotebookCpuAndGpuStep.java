package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_MACHINE_TYPE;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.cloud.notebooks.v1.Instance;
import com.google.cloud.notebooks.v1.NotebookServiceClient;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;

/**
 * Make a direct cloud call using the Google API Client Library for AI notebooks {@link
 * AIPlatformNotebooks}.
 */
public class UpdateAiNotebookCpuAndGpuStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final GcpCloudContextService cloudContextService;

  public UpdateAiNotebookCpuAndGpuStep(
      ControlledAiNotebookInstanceResource resource, GcpCloudContextService cloudContextService) {
    this.resource = resource;
    this.cloudContextService = cloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // TODO: use the working map.
    String newMachineType =
        context
            .getInputParameters()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE, String.class);

    AcceleratorConfig newAcceleratorConfig =
        context
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG,
                AcceleratorConfig.class);

    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    return updateAiNotebookCpuAndGpu(projectId, newMachineType, newAcceleratorConfig);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Case 2: Some update may have occurred.
    String previousMachineType = context.getWorkingMap().get(PREVIOUS_MACHINE_TYPE, String.class);
    AcceleratorConfig previousAcceleratorConfig =
        context.getWorkingMap().get(PREVIOUS_ACCELERATOR_CONFIG, AcceleratorConfig.class);
    String projectId = resource.getProjectId();
    // Attempt to revert cloud update (if it happened).
    return updateAiNotebookCpuAndGpu(projectId, previousMachineType, previousAcceleratorConfig);
  }

  private StepResult updateAiNotebookCpuAndGpu(
      String projectId, String machineType, AcceleratorConfig acceleratorConfig) {
    try (NotebookServiceClient notebookServiceClient = NotebookServiceClient.create()) {
      InstanceName instanceName = resource.toInstanceName(projectId);

      // TODO (aaronwa@):
      // The AI notebook may be stopped in the small window between the above check, and when this
      // update is executed. We should not retry in that case.

      // TODO: Maybe refactor these two into one combined update to simplify the undo process?
      if (machineType != null) {
        RetryUtils.getWithRetryOnException(
            () ->
                notebookServiceClient
                    .setInstanceMachineTypeAsync(
                        com.google.cloud.notebooks.v1.SetInstanceMachineTypeRequest.newBuilder()
                            .setName(instanceName.formatName())
                            .setMachineType(machineType)
                            .build())
                    .get());
      }

      if (acceleratorConfig != null) {
        RetryUtils.getWithRetryOnException(
            () ->
                notebookServiceClient
                    .setInstanceAcceleratorAsync(
                        com.google.cloud.notebooks.v1.SetInstanceAcceleratorRequest.newBuilder()
                            .setName(instanceName.formatName())
                            .setCoreCount(acceleratorConfig.getCoreCount())
                            .setType(Instance.AcceleratorType.valueOf(acceleratorConfig.getType()))
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
