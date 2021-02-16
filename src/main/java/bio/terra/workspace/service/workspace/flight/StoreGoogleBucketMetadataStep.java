package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
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
    final GoogleBucketCreationParameters bucketParams =
        inputMap.get(
            GoogleBucketFlightMapKeys.BUCKET_CREATION_PARAMS.getKey(),
            GoogleBucketCreationParameters.class);
    final String attributeMap = ""; // FIXME
    final ControlledResourceDbModel controlledResourceMetadata =
        ControlledResourceDbModel.builder()
            .setWorkspaceId(inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class))
            .setResourceId(inputMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, UUID.class))
            .setOwner(
                inputMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, String.class))
            .setIsVisible(true)
            .setAttributes(attributeMap)
            .build();
    controlledResourceDao.createControlledResource(controlledResourceMetadata);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // TODO qadelete the entry for workspace ID and resource ID
    return StepResult.getStepResultSuccess();
  }
}
