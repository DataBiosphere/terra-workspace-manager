package bio.terra.workspace.service.resource.controlled.flight.delete.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.services.notebooks.v1.model.Instance;
import java.io.IOException;

/**
 * A step to retrieve the service account used on an AI Platform Notebook instance before deleting
 * the instance.
 *
 * <p>We don't store the service account email anywhere in Workspace Manager metadata, so we have to
 * be sure to retrieve it from the cloud before deleting the notebook instance so that we can also
 * delete the service account.
 */
public class RetrieveNotebookServiceAccountStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public RetrieveNotebookServiceAccountStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(projectId)
            .location(resource.getLocation())
            .instanceId(resource.getInstanceId())
            .build();
    AIPlatformNotebooksCow notebooksCow = crlService.getAIPlatformNotebooksCow();

    try {
      Instance instance = notebooksCow.instances().get(instanceName).execute();
      flightContext
          .getWorkingMap()
          .put(DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL, instance.getServiceAccount());
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Read only step.
    return StepResult.getStepResultSuccess();
  }
}
