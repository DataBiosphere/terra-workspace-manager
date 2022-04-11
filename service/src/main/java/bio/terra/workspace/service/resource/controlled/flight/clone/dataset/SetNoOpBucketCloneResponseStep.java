package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import org.springframework.http.HttpStatus;

public class SetNoOpBucketCloneResponseStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // nothing further to do here or on following steps
    // Build an empty response object
    final ApiClonedControlledGcpBigQueryDataset noOpResult =
        new ApiClonedControlledGcpBigQueryDataset()
            .dataset(null)
            .sourceWorkspaceId(sourceDataset.getWorkspaceId())
            .sourceResourceId(sourceDataset.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    FlightUtils.setResponse(flightContext, noOpResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
