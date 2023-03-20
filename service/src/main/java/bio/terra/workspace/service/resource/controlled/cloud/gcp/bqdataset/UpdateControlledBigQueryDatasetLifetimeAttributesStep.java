package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_PARAMETERS;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;

public class UpdateControlledBigQueryDatasetLifetimeAttributesStep implements Step {
  private final ResourceDao resourceDao;
  private final ControlledBigQueryDatasetResource sourceDataset;

  public UpdateControlledBigQueryDatasetLifetimeAttributesStep(
      ResourceDao resourceDao, ControlledBigQueryDatasetResource sourceDataset) {
    this.resourceDao = resourceDao;
    this.sourceDataset = sourceDataset;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String previousAttributes = sourceDataset.attributesToJson();
    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    final ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        context
            .getInputParameters()
            .get(UPDATE_PARAMETERS, ApiGcpBigQueryDatasetUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    Long newDefaultTableLifetime =
        Optional.ofNullable(updateParameters.getDefaultTableLifetime())
            .orElse(sourceDataset.getDefaultTableLifetime());
    Long newDefaultPartitionLifetime =
        Optional.ofNullable(updateParameters.getDefaultPartitionLifetime())
            .orElse(sourceDataset.getDefaultPartitionLifetime());

    String newAttributes =
        DbSerDes.toJson(
            new ControlledBigQueryDatasetAttributes(
                sourceDataset.getDatasetName(),
                sourceDataset.getProjectId(),
                newDefaultTableLifetime,
                newDefaultPartitionLifetime));

    boolean updated =
        resourceDao.updateResource(
            sourceDataset.getWorkspaceId(),
            sourceDataset.getResourceId(),
            /*name=*/ null,
            /*description=*/ null,
            newAttributes,
            /*cloningInstructions=*/ null);

    if (!updated) {
      throw new RetryException("Failed to update dataset with new data.");
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String previousAttributes =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    if (previousAttributes == null) {
      return StepResult.getStepResultSuccess();
    }
    resourceDao.updateResource(
        sourceDataset.getWorkspaceId(),
        sourceDataset.getResourceId(),
        null,
        null,
        previousAttributes,
        null);
    return StepResult.getStepResultSuccess();
  }
}
