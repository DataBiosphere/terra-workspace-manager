package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
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
    final FlightMap workingMap = flightContext.getWorkingMap();
    final ControlledResourceMetadata controlledResourceMetadata = ControlledResourceMetadata.builder()
        .workspaceId(workingMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class))
        .resourceId(workingMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, UUID.class))
        .owner(workingMap.get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class))
        .associatedApp(null) // FIXME
        .attributes(null)
        .build();
    final UUID resourceId = controlledResourceDao.createControlledResource(controlledResourceMetadata);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // delete the entry for workspace ID and resource ID
    return StepResult.getStepResultSuccess();
  }
}
