package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;

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

    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
