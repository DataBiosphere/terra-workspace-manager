package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGceUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.GcpFlightExceptionUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata.Items;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class RetrieveGceInstanceResourceAttributesStep implements Step {

  private final ControlledGceInstanceResource resource;
  private final CrlService crlService;

  public RetrieveGceInstanceResourceAttributesStep(
      ControlledGceInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    try {
      Instance instance =
          cloudComputeCow
              .instances()
              .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
              .execute();
      Map<String, String> metadata =
          instance.getMetadata().getItems().stream()
              .collect(Collectors.toMap(Items::getKey, Items::getValue));
      ApiGcpGceUpdateParameters existingUpdateParameters =
          new ApiGcpGceUpdateParameters().metadata(metadata);
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
