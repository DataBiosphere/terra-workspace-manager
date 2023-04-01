package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.cloud.notebooks.v1.Instance;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

/**
 * The notebook instance must be stopped for CPU and GPU updates to occur. If the requested CPU
 * and/or GPU update will not change anything, then skip checking if the notebook is stopped.
 *
 * <p>This step also calculates the effective update instructions. If the requested update attribute
 * is equal to the original, then place null in the working map. Otherwise, place the new attribute
 * in the working map. This allows us to skip future update steps if no CPU/GPU update is requested.
 */
public class CheckAiNotebookStoppedForGpuAndCpuUpdateStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;

  public CheckAiNotebookStoppedForGpuAndCpuUpdateStep(
      ControlledAiNotebookInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // If the requested update will not change anything on the cloud, then this step ends
    // successfully (i.e., no need to check if the notebook is stopped).

    // First, calculate the effective update instructions.
    ApiGcpAiNotebookUpdateParameters prevParameters =
        Objects.requireNonNull(
            context
                .getWorkingMap()
                .get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class));

    String previousMachineType = prevParameters.getMachineType();

    AcceleratorConfig previousAcceleratorConfig =
        AcceleratorConfig.fromApiAcceleratorConfig(prevParameters.getAcceleratorConfig());

    ApiGcpAiNotebookUpdateParameters updateParameters =
        context.getInputParameters().get(UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);

    String requestedNewMachineType =
        updateParameters == null ? null : updateParameters.getMachineType();

    AcceleratorConfig requestedNewAcceleratorConfig =
        AcceleratorConfig.fromApiAcceleratorConfig(
            updateParameters == null ? null : updateParameters.getAcceleratorConfig());

    // If attributes are the same as the previous, then no update is required.
    String effectiveMachineType =
        StringUtils.equals(requestedNewMachineType, previousMachineType)
            ? null
            : requestedNewMachineType;

    AcceleratorConfig effectiveAcceleratorConfig =
        Objects.equals(requestedNewAcceleratorConfig, previousAcceleratorConfig)
            ? null
            : requestedNewAcceleratorConfig;

    // Place the effective update instructions in the working map for future steps.
    ApiGcpAiNotebookUpdateParameters effectiveUpdateParameters =
        new ApiGcpAiNotebookUpdateParameters()
            .machineType(effectiveMachineType)
            .acceleratorConfig(
                AcceleratorConfig.toApiAcceleratorConfig(effectiveAcceleratorConfig));

    context.getWorkingMap().put(UPDATE_PARAMETERS, effectiveUpdateParameters);

    // No update requested OR the requested update does not differ from the original attributes.
    if ((requestedNewMachineType == null && requestedNewAcceleratorConfig == null)
        || (effectiveMachineType == null && effectiveAcceleratorConfig == null)) {
      return StepResult.getStepResultSuccess();
    }
    // Otherwise, the requested update changes at least one of the CPU or GPU.

    // Check if the notebook is stopped, so the flight can proceed with updating.
    var projectId = resource.getProjectId();
    InstanceName instanceName = resource.toInstanceName(projectId);

    try {
      com.google.api.services.notebooks.v1.model.Instance instance =
          crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      var instanceState = instance.getState();

      // If stopping, then retry and wait until the instance stops.
      if (instanceState.equals(Instance.State.STOPPING.toString())) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
      // If not stopped (or in the process of stopping), then the flight cannot continue.
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
    // The instance is stopped.
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This is a read-only step checking the state of the notebook instance.
    return StepResult.getStepResultSuccess();
  }
}
