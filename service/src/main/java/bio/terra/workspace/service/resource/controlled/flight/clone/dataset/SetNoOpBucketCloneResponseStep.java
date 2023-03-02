package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import org.springframework.http.HttpStatus;

public class SetNoOpBucketCloneResponseStep implements Step {
  private final ControlledBigQueryDatasetResource sourceDataset;

  public SetNoOpBucketCloneResponseStep(ControlledBigQueryDatasetResource sourceDataset) {
    this.sourceDataset = sourceDataset;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // nothing further to do here or on following steps
    // Build an empty response object
    final ApiClonedControlledGcpBigQueryDataset noOpResult =
        new ApiClonedControlledGcpBigQueryDataset()
            .dataset(null)
            .sourceWorkspaceId(sourceDataset.getWorkspaceId())
            .sourceResourceId(sourceDataset.getResourceId())
            .effectiveCloningInstructions(ApiCloningInstructionsEnum.COPY_NOTHING);
    FlightUtils.setResponse(context, noOpResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
