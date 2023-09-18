package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

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
import bio.terra.workspace.service.resource.GcpFlightExceptionUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.Instance;
import java.io.IOException;
import java.util.Map;

public class RetrieveAiNotebookResourceAttributesStep implements Step {

  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;

  public RetrieveAiNotebookResourceAttributesStep(
      ControlledAiNotebookInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap workingMap = context.getWorkingMap();
    InstanceName instanceName = resource.toInstanceName();
    AIPlatformNotebooksCow notebooksCow = crlService.getAIPlatformNotebooksCow();
    try {
      Instance instance = notebooksCow.instances().get(instanceName).execute();
      Map<String, String> metadata = instance.getMetadata();
      ApiGcpAiNotebookUpdateParameters existingUpdateParameters =
          new ApiGcpAiNotebookUpdateParameters().metadata(metadata);
      workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, existingUpdateParameters);
    } catch (GoogleJsonResponseException e) {
      // Throw bad request exception for malformed parameters
      GcpFlightExceptionUtils.handleGcpBadRequestException(e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
