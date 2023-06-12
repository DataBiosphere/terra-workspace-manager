package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiGcpGceUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** {@link Step} to update cloud attributes (e.g. metadata) for a gce instance. */
public class UpdateGceInstanceAttributesStep implements Step {
  private final ControlledGceInstanceResource resource;
  private final CrlService crlService;

  private final Logger logger = LoggerFactory.getLogger(UpdateGceInstanceAttributesStep.class);

  UpdateGceInstanceAttributesStep(ControlledGceInstanceResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap inputParameters = context.getInputParameters();
    final ApiGcpGceUpdateParameters updateParameters =
        inputParameters.get(UPDATE_PARAMETERS, ApiGcpGceUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    Map<String, String> sanitizedMetadata = new HashMap<>();
    for (var entrySet : updateParameters.getMetadata().entrySet()) {
      if (ControlledGceInstanceResource.RESERVED_METADATA_KEYS.contains(entrySet.getKey())) {
        logger.error("Cannot modify terra reserved keys {}", entrySet.getKey());
        throw new ReservedMetadataKeyException(
            String.format("Cannot modify terra reserved keys %s", entrySet.getKey()));
      }
      sanitizedMetadata.put(entrySet.getKey(), entrySet.getValue());
    }
    return updateInstance(sanitizedMetadata);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final FlightMap workingMap = context.getWorkingMap();
    final ApiGcpGceUpdateParameters prevParameters =
        FlightUtils.getRequired(
            workingMap, PREVIOUS_UPDATE_PARAMETERS, ApiGcpGceUpdateParameters.class);
    try {
      Map<String, String> currentMetadata =
          crlService
              .getCloudComputeCow()
              .instances()
              .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
              .execute()
              .getMetadata()
              .getItems()
              .stream()
              .collect(Collectors.toMap(Items::getKey, Items::getValue));
      currentMetadata.replaceAll((k, v) -> prevParameters.getMetadata().get(k));
      return updateInstance(currentMetadata);
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

  private StepResult updateInstance(Map<String, String> metadataToUpdate) {
    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    try {
      Metadata instanceMetadata =
          cloudComputeCow
              .instances()
              .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
              .execute()
              .getMetadata();

      Map<String, String> updatedMetadata =
          instanceMetadata.getItems().stream()
              .collect(Collectors.toMap(Items::getKey, Items::getValue));
      updatedMetadata.putAll(metadataToUpdate);

      cloudComputeCow
          .instances()
          .setMetadata(
              resource.getProjectId(),
              resource.getZone(),
              resource.getInstanceId(),
              new Metadata()
                  .setItems(
                      updatedMetadata.entrySet().stream()
                          .filter(i -> i.getValue() != null)
                          .map(i -> new Items().setKey(i.getKey()).setValue(i.getValue()))
                          .collect(Collectors.toList()))
                  .setFingerprint(instanceMetadata.getFingerprint()))
          .execute();
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
