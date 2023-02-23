package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.exceptions.ConcurrentFlightModificationException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for releasing all private resource cleanup claims (i.e. setting all cleanup_flight_id
 * columns set in {@code ClaimUserPrivateResourceStep} to null).
 */
public class ReleasePrivateResourceCleanupClaimsStep implements Step {

  private final Logger logger =
      LoggerFactory.getLogger(ReleasePrivateResourceCleanupClaimsStep.class);

  private final UUID workspaceUuid;
  private final String userEmail;
  private final ResourceDao resourceDao;

  public ReleasePrivateResourceCleanupClaimsStep(
      UUID workspaceUuid, String userEmail, ResourceDao resourceDao) {
    this.workspaceUuid = workspaceUuid;
    this.userEmail = userEmail;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    resourceDao.releasePrivateResourceCleanupClaims(
        workspaceUuid, userEmail, context.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    // We release the lock on resources in the do step, so we need to attempt to re-acquire it
    // here before undoing. If another flight has claimed any of the resources in the meantime, we
    // cannot undo them further without clobbering, so this is a dismal failure.
    List<ControlledResource> relockedResources =
        resourceDao.claimCleanupForWorkspacePrivateResources(
            workspaceUuid, userEmail, context.getFlightId());
    Set<UUID> relockedResourceIds =
        relockedResources.stream().map(WsmResource::getResourceId).collect(Collectors.toSet());

    List<ResourceRolePair> originalResourceRolePairs =
        workingMap.get(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    Set<UUID> originalResourceIds =
        originalResourceRolePairs.stream()
            .map(r -> r.getResource().getResourceId())
            .collect(Collectors.toSet());

    if (!relockedResourceIds.containsAll(originalResourceIds)) {
      logger.error(
          "Unable to re-acquire cleanup_flight_id lock on all private resources being cleaned up for flight {}.",
          context.getFlightId());
      throw new ConcurrentFlightModificationException(
          "Unable to re-acquire cleanup_flight_id lock on all private resources being cleaned up");
    }
    // It's fine if this step locks resources that the DO direction didn't (e.g. resources created
    // while this flight was running), as we will release all resources held by this flight later in
    // UNDO steps.
    return StepResult.getStepResultSuccess();
  }
}
