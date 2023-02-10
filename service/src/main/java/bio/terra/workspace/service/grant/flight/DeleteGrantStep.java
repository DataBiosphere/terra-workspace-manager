package bio.terra.workspace.service.grant.flight;

import static bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight.SKIP;
import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.GrantDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step 3: delete the grant row */
public class DeleteGrantStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteGrantStep.class);
  private final GrantDao grantDao;
  private final UUID grantId;

  public DeleteGrantStep(GrantDao grantDao, UUID grantId) {
    this.grantDao = grantDao;
    this.grantId = grantId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Boolean skip = context.getWorkingMap().get(SKIP, Boolean.class);
    if (TRUE.equals(skip)) {
      logger.debug("Skipping delete grant of {}", grantId);
    } else {
      grantDao.deleteGrant(grantId, context.getFlightId());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // No undo possible if the delete fails. Dismal failure. Requires a human
    // to look at the DB and see what is up with the grant.
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException("Possible corruption of grant id" + grantId));
  }
}
