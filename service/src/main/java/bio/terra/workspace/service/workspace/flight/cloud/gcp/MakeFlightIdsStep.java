package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.removeuser.ResourceRolePair;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generate flight ids and store them in the working map. The ids need to be stable across runs of
 * the RemoveNativeAccessToPrivateResourcesStep.
 */
public class MakeFlightIdsStep implements Step {
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    List<ResourceRolePair> resourceRolesPairs =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});

    Map<UUID, String> flightIds = new HashMap<>();

    for (ResourceRolePair resourceRolePair : resourceRolesPairs) {
      String flightId = UUID.randomUUID().toString();
      flightIds.put(resourceRolePair.getResource().getResourceId(), flightId);
    }
    context.getWorkingMap().put(WorkspaceFlightMapKeys.FLIGHT_IDS, flightIds);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
