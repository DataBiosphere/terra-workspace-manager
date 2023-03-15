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
import bio.terra.workspace.db.ResourceDao;
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
  private final ClientConfig clientConfig;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  private final GcpCloudContextService gcpCloudContextService;
  private final ResourceDao resourceDao;

  private final GcpCloudContextService cloudContextService;
  private final CrlService crlService;

  public UpdateAiNotebookCpuAndGpuStep(
      ControlledAiNotebookInstanceResource resource,
      ClientConfig clientConfig,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      GcpCloudContextService gcpCloudContextService,
      ResourceDao resourceDao,
      GcpCloudContextService cloudContextService,
      CrlService crlService) {
    this.resource = resource;
    this.clientConfig = clientConfig;
    this.samService = samService;
    this.userRequest = userRequest;
    this.gcpCloudContextService = gcpCloudContextService;
    this.resourceDao = resourceDao;
    this.cloudContextService = cloudContextService;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // The notebook instance must be stopped for CPU and GPU updates to occur.
    // If requested update will not change anything on the cloud, then this flight ends successfully
    // (no further action is needed - i.e., no update call).
    if (noEffectiveUpdateRequested(context)) {
      return StepResult.getStepResultSuccess();
    }

    // Otherwise, the requested update changes at least one of the CPU or GPU.
    // Check if the notebook is stopped, so we can update later in the flight.
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);

    try {
      com.google.api.services.notebooks.v1.model.Instance instance =
          crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      // If not stopped, then we cannot proceed with the update.
      var instanceState = instance.getState();

      // Retry and wait until the instance stops.
      if (instanceState.equals(Instance.State.STOPPING.toString())) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
      // In all other cases when the instance is not stopped, a FATAL result should be returned.
      if (!instanceState.equals(Instance.State.STOPPED.toString())) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new IllegalStateException(
                "Notebook instance must be stopped before updating the CPU or GPU configuration. The current notebook state is: %s (instanceName: %s; id: %s)"
                    .formatted(
                        instance.getState(),
                        instanceName.formatName(),
                        resource.getWsmResourceFields().getResourceId())));
      }
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

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
    return updateAiNotebookCpuAndGpu(projectId, newMachineType, newAcceleratorConfig);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Case 1: No update would have been performed in the "do" step.
    // The flight context working map is never modified during any part of this entire flight, so
    // the result of this update check function is identical to the result in the "do" step.
    if (noEffectiveUpdateRequested(context)) {
      return StepResult.getStepResultSuccess();
    }
    // Case 2: Some update may have occurred.
    String previousMachineType = context.getWorkingMap().get(PREVIOUS_MACHINE_TYPE, String.class);
    AcceleratorConfig previousAcceleratorConfig =
        context.getWorkingMap().get(PREVIOUS_ACCELERATOR_CONFIG, AcceleratorConfig.class);
    String projectId = resource.getProjectId();
    // Attempt to revert cloud update.
    return updateAiNotebookCpuAndGpu(projectId, previousMachineType, previousAcceleratorConfig);
  }

  // Returns true if no update is required (the update will not change the previous attributes).
  private boolean noEffectiveUpdateRequested(FlightContext context) {
    String previousMachineType =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_MACHINE_TYPE, String.class);

    AcceleratorConfig previousAcceleratorConfig =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_ACCELERATOR_CONFIG,
                AcceleratorConfig.class);

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

    // No update requested.
    if (newMachineType == null && newAcceleratorConfig == null) {
      return true;
    }

    // Check if the requested update differs from the previous.
    return Objects.equals(newMachineType, previousMachineType)
        && (Objects.equals(newAcceleratorConfig, previousAcceleratorConfig));
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
