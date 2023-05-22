package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate flight ids and store them in the working map. The ids need to be stable across runs of
 * the RunDeleteCloudContextFlightStep.
 */
public class MakeFlightIdsStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(MakeFlightIdsStep.class);

  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;

  public MakeFlightIdsStep(UUID workspaceUuid, WorkspaceDao workspaceDao) {
    this.workspaceUuid = workspaceUuid;
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Map<CloudPlatform, String> flightIds = new HashMap<>();

    // For each cloud context in the workspace, run the cloud context delete flight
    for (CloudPlatform cloudPlatform : workspaceDao.listCloudPlatforms(workspaceUuid)) {
      String flightId = UUID.randomUUID().toString();
      flightIds.put(cloudPlatform, flightId);
      logger.info("Made flight id {} for deleting {} cloud context", flightId, cloudPlatform);
    }
    context.getWorkingMap().put(WorkspaceFlightMapKeys.FLIGHT_IDS, flightIds);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
