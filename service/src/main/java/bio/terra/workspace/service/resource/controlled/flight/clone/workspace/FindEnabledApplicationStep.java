package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.application.able.AbleEnum;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindEnabledApplicationStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(FindEnabledApplicationStep.class);
  private final ApplicationDao applicationDao;

  public FindEnabledApplicationStep(ApplicationDao applicationDao) {
    this.applicationDao = applicationDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID);
    final var sourceWorkspaceId =
        context
            .getInputParameters()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    int offset = 0;
    final int limit = 100;
    List<WsmWorkspaceApplication> batch;
    final List<String> result = new ArrayList<>();
    do {
      batch = applicationDao.listWorkspaceApplications(sourceWorkspaceId, offset, limit);
      offset += limit;
      final List<WsmWorkspaceApplication> enabledApplications =
          batch.stream().filter(WsmWorkspaceApplication::isEnabled).toList();
      enabledApplications.forEach(r -> result.add(r.getApplication().getApplicationId()));
    } while (batch.size() == limit);

    logger.info("Will enable applications {} in workspace {}", result, sourceWorkspaceId);

    context.getWorkingMap().put(WorkspaceFlightMapKeys.APPLICATION_IDS, result);
    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.ENABLE);

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo; no side effects.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
