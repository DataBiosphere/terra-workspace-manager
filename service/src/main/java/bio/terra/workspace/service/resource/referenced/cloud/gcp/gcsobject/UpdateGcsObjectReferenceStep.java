package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.apache.commons.lang3.StringUtils;

public class UpdateGcsObjectReferenceStep implements Step {

  public UpdateGcsObjectReferenceStep() {}

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
            ReferencedGcsObjectAttributes.class);
    var oldAttributes = dbUpdater.getOriginalAttributes(ReferencedGcsObjectAttributes.class);

    String bucketName =
        StringUtils.isEmpty(attributes.getBucketName())
            ? oldAttributes.getBucketName()
            : attributes.getBucketName();
    String objectName =
        StringUtils.isEmpty(attributes.getObjectName())
            ? oldAttributes.getObjectName()
            : attributes.getObjectName();

    dbUpdater.updateAttributes(new ReferencedGcsObjectAttributes(bucketName, objectName));
    context.getWorkingMap().put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
