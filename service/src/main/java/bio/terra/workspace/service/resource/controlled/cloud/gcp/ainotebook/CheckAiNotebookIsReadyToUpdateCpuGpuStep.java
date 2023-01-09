package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.MACHINE_TYPE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_TO_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_TO_MACHINE_TYPE;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.AcceleratorConfig;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * This step check if the requested machine configuration is indeed different from the existing
 * machine configuration. If there is indeed a requested change, the step then verify the virtual
 * machine is stopped before sending an update request. GCP requires a machine to stop before
 * updating.
 */
public class CheckAiNotebookIsReadyToUpdateCpuGpuStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;

  private final Logger logger =
      LoggerFactory.getLogger(CheckAiNotebookIsReadyToUpdateCpuGpuStep.class);

  public CheckAiNotebookIsReadyToUpdateCpuGpuStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.cloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputMap = context.getInputParameters();
    FlightMap workingMap = context.getWorkingMap();
    String machineType = inputMap.get(MACHINE_TYPE, String.class);
    AcceleratorConfig acceleratorConfig = inputMap.get(ACCELERATOR_CONFIG, AcceleratorConfig.class);
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);

    try {
      var instance = crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      if (instance.getMachineType().equals(machineType)) { // If updating cpu
        machineType = null;
      }
      if (acceleratorConfig != null
          && acceleratorConfig.getAcceleratorType() != null) { // If updating gpu
        if (instance.getAcceleratorConfig().getType().equals(acceleratorConfig.getAcceleratorType())
            && instance
                .getAcceleratorConfig()
                .getCoreCount()
                .equals(acceleratorConfig.getAcceleratorCount().longValue())) {
          acceleratorConfig = null;
        }
      }
      if ((machineType != null || acceleratorConfig != null)
          && !instance.getState().equals("STOPPED")) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new IllegalStateException("Notebook instance has to be stopped before updating."));
      }
      // The machine doesn't need to be updated if the requested machine configuration
      // is identical with the existing machine configuration. we only update the machine
      // when there is a change
      workingMap.put(UPDATE_TO_MACHINE_TYPE, machineType);
      workingMap.put(UPDATE_TO_ACCELERATOR_CONFIG, acceleratorConfig);
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
    // This step is read-only, so nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
