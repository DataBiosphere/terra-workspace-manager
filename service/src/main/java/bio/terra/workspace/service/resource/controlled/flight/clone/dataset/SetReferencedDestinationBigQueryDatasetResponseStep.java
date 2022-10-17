package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import org.springframework.http.HttpStatus;

/**
 * Sets flight response to destination dataset.
 *
 * <p>Note - This can't be done in SetReferencedDestinationBigQueryDatasetInWorkingMapStep, because
 * CreateReferenceMetadataStep sets flight response to dest resource ID. So this must be done after
 * CreateReferenceMetadataStep.
 */
public class SetReferencedDestinationBigQueryDatasetResponseStep implements Step {

  public SetReferencedDestinationBigQueryDatasetResponseStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ControlledResource sourceDataset =
        context.getInputParameters().get(ResourceKeys.RESOURCE, ControlledResource.class);
    ReferencedBigQueryDatasetResource destDataset =
        context
            .getWorkingMap()
            .get(
                ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE,
                ReferencedBigQueryDatasetResource.class);

    final ApiClonedControlledGcpBigQueryDataset apiClonedDataset =
        new ApiClonedControlledGcpBigQueryDataset()
            .effectiveCloningInstructions(ApiCloningInstructionsEnum.REFERENCE)
            .dataset(destDataset.toApiResource())
            .sourceWorkspaceId(sourceDataset.getWorkspaceId())
            .sourceResourceId(sourceDataset.getResourceId());
    FlightUtils.setResponse(context, apiClonedDataset, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  // No side effects to undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
