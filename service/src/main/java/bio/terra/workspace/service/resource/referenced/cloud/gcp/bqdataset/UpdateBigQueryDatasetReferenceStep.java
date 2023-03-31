package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.apache.commons.lang3.StringUtils;

public class UpdateBigQueryDatasetReferenceStep implements Step {

  public UpdateBigQueryDatasetReferenceStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var dbUpdater =
        FlightUtils.getRequired(
            context.getWorkingMap(),
            WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER,
            DbUpdater.class);
    var attributes =
        FlightUtils.getRequired(
            context.getInputParameters(),
            WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS,
            ReferencedBigQueryDatasetAttributes.class);
    var oldAttributes = dbUpdater.getOriginalAttributes(ReferencedBigQueryDatasetAttributes.class);

    String projectId =
        StringUtils.isEmpty(attributes.getProjectId())
            ? oldAttributes.getProjectId()
            : attributes.getProjectId();
    String datasetName =
        StringUtils.isEmpty(attributes.getDatasetName())
            ? oldAttributes.getDatasetName()
            : attributes.getDatasetName();

    dbUpdater.updateAttributes(new ReferencedBigQueryDatasetAttributes(projectId, datasetName));
    context.getWorkingMap().put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
