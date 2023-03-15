package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.api.services.notebooks.v1.model.Instance;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Objects;

/**
 * Updating either the CPU and/or GPU requires the notebook instance to be stopped. If no update is
 * specified (null), or the requested CPU/GPU update will not change anything, then skip checking if
 * the notebook is stopped.
 */
// TODO (aaronwa@): Merge this step with the update step.
public class CheckAiNotebookStoppedForGpuAndCpuUpdateStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;

  public CheckAiNotebookStoppedForGpuAndCpuUpdateStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.cloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
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
      return StepResult.getStepResultSuccess();
    }

    // The requested update will not change the CPU/GPU. No need to update in future steps.
    if (Objects.equals(newMachineType, previousMachineType)
        && (Objects.equals(newAcceleratorConfig, previousAcceleratorConfig))) {
      return StepResult.getStepResultSuccess();
    }
    // Otherwise, at least one of the CPU or GPU will be updated. Check if the notebook is stopped,
    // so we can update in the next step.
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);

    try {
      Instance instance =
          crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      // If stopped, then we cannot proceed with the update.
      if (instance
          .getState()
          .equals(com.google.cloud.notebooks.v1.Instance.State.STOPPED.toString())) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new IllegalStateException(
                "Notebook instance must be stopped before updating the CPU or GPU configuration."));
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

    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
