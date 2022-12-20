package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_TO_REGION_MAP;
import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.common.utils.FlightUtils.setResponse;
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
import org.springframework.http.HttpStatus;
import scala.util.control.TailCalls.Cont;

public class UpdateGcpResourcesRegionStep implements Step {

  private final ResourceDao resourceDao;
  public UpdateGcpResourcesRegionStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    validateRequiredEntries(workingMap, CONTROLLED_RESOURCE_TO_REGION_MAP);
    Map<ControlledResource, String> resourcesToRegionMap =
        workingMap.get(CONTROLLED_RESOURCE_TO_REGION_MAP,
            new TypeReference<>() {});
    List<ControlledResource> updatedResources = new ArrayList<>();
    for (var resource: resourcesToRegionMap.keySet()) {
      boolean updated = resourceDao.updateControlledResourceRegion(resource.getWorkspaceId(), resource.getResourceId(), resourcesToRegionMap.get(resource));
      if (updated) {
        updatedResources.add(resource);
      }
    }
    setResponse(context, updatedResources, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    Map<ControlledResource, String> resourcesToRegionMap =
        context.getWorkingMap().get(CONTROLLED_RESOURCE_TO_REGION_MAP,
            new TypeReference<>() {});
    if (resourcesToRegionMap == null) {
      return StepResult.getStepResultSuccess();
    }
    for (var resource: resourcesToRegionMap.keySet()) {
      resourceDao.updateControlledResourceRegion(resource.getWorkspaceId(), resource.getResourceId(), /*region=*/ null);
    }
    return StepResult.getStepResultSuccess();
  }
}
