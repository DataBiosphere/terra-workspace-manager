package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import static bio.terra.workspace.common.utils.FlightUtils.setResponse;
import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_REGION_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UpdateGcpResourcesRegionStep implements Step {

  private final ResourceDao resourceDao;

  public UpdateGcpResourcesRegionStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    validateRequiredEntries(
        workingMap,
        CONTROLLED_RESOURCE_ID_TO_REGION_MAP,
        CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP);
    Map<UUID, String> resourceIdsToRegionMap =
        workingMap.get(CONTROLLED_RESOURCE_ID_TO_REGION_MAP, new TypeReference<>() {});
    Map<UUID, String> resourceIdsToWorkspaceIdMap =
        workingMap.get(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, new TypeReference<>() {});
    List<ControlledResource> updatedResources = new ArrayList<>();
    for (var id : resourceIdsToRegionMap.keySet()) {
      boolean updated =
          resourceDao.updateControlledResourceRegion(id, resourceIdsToRegionMap.get(id));
      if (updated) {
        updatedResources.add(
            resourceDao
                .getResource(UUID.fromString(resourceIdsToWorkspaceIdMap.get(id)), id)
                .castToControlledResource());
      }
    }
    setResponse(context, updatedResources, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    Map<UUID, String> resourcesToRegionMap =
        context.getWorkingMap().get(CONTROLLED_RESOURCE_ID_TO_REGION_MAP, new TypeReference<>() {});
    if (resourcesToRegionMap == null) {
      return StepResult.getStepResultSuccess();
    }
    for (var resourceId : resourcesToRegionMap.keySet()) {
      resourceDao.updateControlledResourceRegion(resourceId, /*region=*/ null);
    }
    return StepResult.getStepResultSuccess();
  }
}
