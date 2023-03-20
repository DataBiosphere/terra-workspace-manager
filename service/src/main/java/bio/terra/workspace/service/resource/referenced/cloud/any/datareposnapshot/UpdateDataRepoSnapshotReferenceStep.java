package bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.apache.commons.lang3.StringUtils;

public class UpdateDataRepoSnapshotReferenceStep implements Step {

  public UpdateDataRepoSnapshotReferenceStep() {}

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
            ReferencedDataRepoSnapshotAttributes.class);
    var oldAttributes = dbUpdater.getOriginalAttributes(ReferencedDataRepoSnapshotAttributes.class);

    String instanceName =
        StringUtils.isEmpty(attributes.getInstanceName())
            ? oldAttributes.getInstanceName()
            : attributes.getInstanceName();
    String snapshotId =
        StringUtils.isEmpty(attributes.getSnapshotId())
            ? oldAttributes.getSnapshotId()
            : attributes.getSnapshotId();

    dbUpdater.updateAttributes(new ReferencedDataRepoSnapshotAttributes(instanceName, snapshotId));
    context.getWorkingMap().put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
