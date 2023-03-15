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

    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
