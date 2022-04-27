package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetrieveBigQueryDatasetCloudAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveBigQueryDatasetCloudAttributesStep.class);
  private final ControlledBigQueryDatasetResource datasetResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public RetrieveBigQueryDatasetCloudAttributesStep(
      ControlledBigQueryDatasetResource datasetResource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.datasetResource = datasetResource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String projectId =
        gcpCloudContextService.getRequiredGcpProject(datasetResource.getWorkspaceUuid());
    final String datasetId = datasetResource.getDatasetName();
    final BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();

    // get the existing dataset
    Dataset existingDataset;
    try {
      existingDataset = bigQueryCow.datasets().get(projectId, datasetId).execute();
      if (existingDataset == null) {
        IllegalStateException isEx =
            new IllegalStateException("No dataset found with id " + datasetId);
        logger.error("No dataset found with id {}.", datasetId, isEx);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, isEx);
      }
    } catch (IOException ioEx) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ioEx);
    }

    // save the current attributes in the working map, in case something goes wrong and we need to
    // undo the update
    final ApiGcpBigQueryDatasetUpdateParameters updateParametersForUndo =
        BigQueryApiConversions.toUpdateParameters(existingDataset);
    workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, updateParametersForUndo);

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
