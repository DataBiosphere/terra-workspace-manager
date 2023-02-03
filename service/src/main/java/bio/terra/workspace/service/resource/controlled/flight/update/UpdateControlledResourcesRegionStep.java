package bio.terra.workspace.service.resource.controlled.flight.update;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class UpdateControlledResourcesRegionStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(UpdateControlledResourcesRegionStep.class);
  private final ResourceDao resourceDao;
  private final boolean isWetRun;

  public UpdateControlledResourcesRegionStep(ResourceDao resourceDao, boolean isWetRun) {
    this.resourceDao = resourceDao;
    this.isWetRun = isWetRun;
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
    for (var pair : resourceIdsToRegionMap.entrySet()) {
      if (isWetRun) {
        boolean updated =
            resourceDao.updateControlledResourceRegion(pair.getKey(), pair.getValue());
        if (updated) {
          updatedResources.add(
              resourceDao
                  .getResource(UUID.fromString(pair.getValue()), pair.getKey())
                  .castToControlledResource());
        }
      } else {
        logger.info(
            "Dry run to update resource {} in workspace {} to region {}",
            pair.getKey(),
            pair.getValue(),
            resourceIdsToRegionMap.get(pair.getKey()));
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
