package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
import java.util.UUID;

public class StoreGoogleBucketMetadataStep implements Step {

  private final ControlledResourceDao controlledResourceDao;

  public StoreGoogleBucketMetadataStep(ControlledResourceDao controlledResourceDao) {
    this.controlledResourceDao = controlledResourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();
    final ControlledResourceMetadata controlledResourceMetadata =
        ControlledResourceMetadata.builder()
            .workspaceId(inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class))
            .resourceId(inputMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, UUID.class))
            .owner(
                inputMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, String.class))
            .isVisible(true)
            // TODO: we may not need to permanently store the bucket parameters in the DB
            .attributes(
                inputMap.get(
                    GoogleBucketFlightMapKeys.BUCKET_CREATION_PARAMS.getKey(),
                    GoogleBucketCreationParameters.class))
            .build();
    controlledResourceDao.createControlledResource(controlledResourceMetadata);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // delete the entry for workspace ID and resource ID
    return StepResult.getStepResultSuccess();
  }
}
