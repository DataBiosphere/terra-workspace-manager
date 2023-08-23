package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;

public class UpdateBigQueryDatasetStep implements Step {
  private final CrlService crlService;

  public UpdateBigQueryDatasetStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        flightContext
            .getInputParameters()
            .get(UPDATE_PARAMETERS, ApiGcpBigQueryDatasetUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    FlightMap workingMap = flightContext.getWorkingMap();
    DbUpdater dbUpdater =
        FlightUtils.getRequired(
            workingMap, WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, DbUpdater.class);
    var originalAttributes =
        dbUpdater.getOriginalAttributes(ControlledBigQueryDatasetAttributes.class);

    // Compute the updates, if any
    // Capture changes for the dbUpdater
    Long attributeTableLifetime = originalAttributes.getDefaultTableLifetime();
    Long attributePartitionLifetime = originalAttributes.getDefaultPartitionLifetime();

    // Compute what should be changed
    NewLifetime newTableLifetime =
        computeNewLifetimeSecs(attributeTableLifetime, updateParameters.getDefaultTableLifetime());
    NewLifetime newPartitionLifetime =
        computeNewLifetimeSecs(
            attributePartitionLifetime, updateParameters.getDefaultPartitionLifetime());

    // If there are updates, set the changes in the dataset and attributes variables
    if (newTableLifetime.changed()) {
      attributeTableLifetime = newTableLifetime.newValue();
    }
    if (newPartitionLifetime.changed()) {
      attributePartitionLifetime = newPartitionLifetime.newValue();
    }

    // Apply the new attributes
    if (newTableLifetime.changed() || newPartitionLifetime.changed()) {
      var newAttributes =
          new ControlledBigQueryDatasetAttributes(
              originalAttributes.getDatasetName(),
              originalAttributes.getProjectId(),
              attributeTableLifetime,
              attributePartitionLifetime);
      dbUpdater.updateAttributes(newAttributes);
      workingMap.put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);
      return updateDataset(newAttributes);
    }

    return StepResult.getStepResultSuccess();
  }

  // Restore the previous values of the update parameters
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        flightContext
            .getInputParameters()
            .get(UPDATE_PARAMETERS, ApiGcpBigQueryDatasetUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    FlightMap workingMap = flightContext.getWorkingMap();
    DbUpdater dbUpdater =
        FlightUtils.getRequired(
            workingMap, WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, DbUpdater.class);
    var originalAttributes =
        dbUpdater.getOriginalAttributes(ControlledBigQueryDatasetAttributes.class);
    return updateDataset(originalAttributes);
  }

  private StepResult updateDataset(ControlledBigQueryDatasetAttributes attributes) {
    BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();

    Dataset dataset;
    try {
      dataset =
          CrlService.getBigQueryDataset(
              bigQueryCow, attributes.getProjectId(), attributes.getDatasetName());
      if (dataset == null) {
        throw new ResourceNotFoundException(
            "No dataset found with id " + attributes.getDatasetName());
      }
      dataset.setDefaultTableExpirationMs(
          BigQueryApiConversions.toBqExpirationTime(attributes.getDefaultTableLifetime()));
      dataset.setDefaultPartitionExpirationMs(
          BigQueryApiConversions.toBqExpirationTime(attributes.getDefaultPartitionLifetime()));
      crlService.updateBigQueryDataset(
          bigQueryCow, attributes.getProjectId(), attributes.getDatasetName(), dataset);
      return StepResult.getStepResultSuccess();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  /**
   * Helper method to compute change and updated value. if update == null, no change if update == 0,
   * if null != original value, set to null update is a value, if value != original value, set to
   * value
   *
   * @param originalLifetime original lifetime from database
   * @param updateLifetime new lifetime from update parameters
   * @return pair of change and new value
   */
  private NewLifetime computeNewLifetimeSecs(Long originalLifetime, Long updateLifetime) {
    if (updateLifetime == null) {
      return new NewLifetime(false, null);
    }

    if (updateLifetime == 0) {
      return new NewLifetime((originalLifetime != null), null);
    }
    // update is a non-null value
    return new NewLifetime(!updateLifetime.equals(originalLifetime), updateLifetime);
  }

  private record NewLifetime(boolean changed, Long newValue) {}
}
