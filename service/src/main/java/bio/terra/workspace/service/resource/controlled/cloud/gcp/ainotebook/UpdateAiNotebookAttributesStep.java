package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * {@link Step} to update cloud attributes (e.g. metadata) for a ai notebook instance.
 */
public class UpdateAiNotebookAttributesStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;

  UpdateAiNotebookAttributesStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService
  ) {
    this.resource = resource;
    this.crlService = crlService;
    this.cloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap inputMap = context.getInputParameters();
    final ApiGcpAiNotebookUpdateParameters updateParameters =
        inputMap.get(UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);

    return updateAiNotebook(updateParameters);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final FlightMap workingMap = context.getWorkingMap();
    final ApiGcpAiNotebookUpdateParameters prevParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);
    var projectId =
        cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      var currentMetadata = notebooks.instances().get(instanceName).execute().getMetadata();
      for (var entry: currentMetadata.entrySet()) {
        if (!prevParameters.getMetadata().containsKey(entry.getKey())) {
          // reset the new key entry to "" value because the gcp api does not allow deleting
          // metadata item so we can't simply undo the add.
          currentMetadata.put(entry.getKey(), "");
        }
      }
      return updateAiNotebook(new ApiGcpAiNotebookUpdateParameters().metadata(currentMetadata));
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode() || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  private StepResult updateAiNotebook(ApiGcpAiNotebookUpdateParameters updateParameters) {
    var projectId =
        cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      notebooks.instances().updateMetadataItems(instanceName, updateParameters.getMetadata()).execute();
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode() || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
