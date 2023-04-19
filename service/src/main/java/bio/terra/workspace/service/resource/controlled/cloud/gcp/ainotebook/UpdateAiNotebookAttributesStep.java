package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

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
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** {@link Step} to update cloud attributes (e.g. metadata) for a ai notebook instance. */
public class UpdateAiNotebookAttributesStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;

  private final Logger logger = LoggerFactory.getLogger(UpdateAiNotebookAttributesStep.class);

  UpdateAiNotebookAttributesStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.cloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputParameters = context.getInputParameters();
    ApiGcpAiNotebookUpdateParameters updateParameters =
        inputParameters.get(UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    Map<String, String> sanitizedMetadata = new HashMap<>();
    for (var entrySet : updateParameters.getMetadata().entrySet()) {
      if (ControlledAiNotebookInstanceResource.RESERVED_METADATA_KEYS.contains(entrySet.getKey())) {
        logger.error("Cannot modify terra reserved keys {}", entrySet.getKey());
        throw new ReservedMetadataKeyException(
            String.format("Cannot modify terra reserved keys %s", entrySet.getKey()));
      }
      sanitizedMetadata.put(entrySet.getKey(), entrySet.getValue());
    }
    return updateAiNotebook(
        sanitizedMetadata, cloudContextService.getRequiredGcpProject(resource.getWorkspaceId()));
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    ApiGcpAiNotebookUpdateParameters prevParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    try {
      var currentMetadata =
          crlService
              .getAIPlatformNotebooksCow()
              .instances()
              .get(resource.toInstanceName(projectId))
              .execute()
              .getMetadata();
      // reset the new key entry to "" value because the gcp api does not allow deleting
      // metadata item so we can't simply undo the add.
      currentMetadata.replaceAll((k, v) -> prevParameters.getMetadata().getOrDefault(k, ""));
      return updateAiNotebook(currentMetadata, projectId);
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  private StepResult updateAiNotebook(Map<String, String> metadataToUpdate, String projectId) {
    InstanceName instanceName = resource.toInstanceName(projectId);
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      notebooks.instances().updateMetadataItems(instanceName, metadataToUpdate).execute();
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
}
