package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.service.crl.CrlService.getBigQueryDataset;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.BigQueryApiConversions.fromBqExpirationTime;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_PARAMETERS;

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
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateBigQueryDatasetStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(UpdateBigQueryDatasetStep.class);
  private final ControlledBigQueryDatasetResource datasetResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public UpdateBigQueryDatasetStep(
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
    final FlightMap inputMap = flightContext.getInputParameters();
    final ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        inputMap.get(UPDATE_PARAMETERS, ApiGcpBigQueryDatasetUpdateParameters.class);

    return updateDataset(updateParameters);
  }

  // Restore the previous values of the update parameters
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final ApiGcpBigQueryDatasetUpdateParameters previousUpdateParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpBigQueryDatasetUpdateParameters.class);

    return updateDataset(previousUpdateParameters);
  }

  private StepResult updateDataset(
      @Nullable ApiGcpBigQueryDatasetUpdateParameters updateParameters) {
    if (updateParameters == null) {
      // nothing to change
      logger.info("No update parameters supplied, so no changes to make.");
      return StepResult.getStepResultSuccess();
    }
    final String projectId =
        gcpCloudContextService.getRequiredGcpProject(datasetResource.getWorkspaceId());
    final String datasetId = datasetResource.getDatasetName();
    final BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();
    try {
      // get the existing dataset
      Dataset existingDataset = getBigQueryDataset(bigQueryCow, projectId, datasetId);
      if (existingDataset == null) {
        IllegalStateException isEx =
            new IllegalStateException("No dataset found to update with id " + datasetId);
        logger.error("No dataset found to update with id {}.", datasetId, isEx);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, isEx);
      }

      final Long newDefaultTableLifetime = updateParameters.getDefaultTableLifetime();
      final Long newDefaultPartitionLifetime = updateParameters.getDefaultPartitionLifetime();

      final boolean defaultTableLifetimeChanged =
          valueChanged(
              newDefaultTableLifetime,
              fromBqExpirationTime(existingDataset.getDefaultTableExpirationMs()));
      final boolean defaultPartitionLifetimeChanged =
          valueChanged(
              newDefaultPartitionLifetime,
              fromBqExpirationTime(existingDataset.getDefaultPartitionExpirationMs()));
      if (defaultTableLifetimeChanged) {
        existingDataset.setDefaultTableExpirationMs(
            BigQueryApiConversions.toBqExpirationTime(newDefaultTableLifetime));
      }
      if (defaultPartitionLifetimeChanged) {
        existingDataset.setDefaultPartitionExpirationMs(
            BigQueryApiConversions.toBqExpirationTime(newDefaultPartitionLifetime));
      }
      if (defaultTableLifetimeChanged || defaultPartitionLifetimeChanged) {
        crlService.updateBigQueryDataset(bigQueryCow, projectId, datasetId, existingDataset);
      } else {
        logger.info(
            "Cloud attributes for Dataset {} were not changed as all inputs were null or unchanged.",
            datasetId);
      }
      return StepResult.getStepResultSuccess();
    } catch (IOException ioEx) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ioEx);
    }
  }

  /**
   * Helper method to check if the default expiration time fields changed. Since this WSM API is a
   * PATCH, not an UPDATE, a null for the new value means no update.
   */
  private static boolean valueChanged(Long newVal, Long prevVal) {
    return newVal == null ? false : !newVal.equals(prevVal);
  }
}
