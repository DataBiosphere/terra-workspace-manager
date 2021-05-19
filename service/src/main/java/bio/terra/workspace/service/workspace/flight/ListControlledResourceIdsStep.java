package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;

/**
 * A step to list the controlled resources in a workspace, potentially filtered to a single cloud,
 * and store that in the Flight's working map.
 */
public class ListControlledResourceIdsStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final CloudPlatform cloudPlatform;

  public ListControlledResourceIdsStep(
      ResourceDao resourceDao, UUID workspaceId, CloudPlatform cloudPlatform) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    List<ControlledResource> controlledResourceIdList =
        resourceDao.listControlledResources(workspaceId, cloudPlatform);
    workingMap.put(ControlledResourceKeys.CONTROLLED_RESOURCE_LIST, controlledResourceIdList);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
