package bio.terra.workspace.service.grant.flight;

import static bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight.SKIP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.GrantDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 1: lock the grant row and return the grant data On undo, unlock the grant row if it exists
 * and this flight has the lock.
 */
public class LockGrantStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(LockGrantStep.class);
  private final GrantDao grantDao;
  private final UUID grantId;

  public LockGrantStep(GrantDao grantDao, UUID grantId) {
    this.grantDao = grantDao;
    this.grantId = grantId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Try to lock. If we fail, we skip the rest of the flight.
    // Failing would indicate either the grant is gone (revoked by another flight)
    // or it is locked by another flight.
    boolean gotLock = grantDao.lockGrant(grantId, context.getFlightId());
    logger.debug("Attempt to lock grant {}. Result of lock is: {}", grantId, gotLock);
    context.getWorkingMap().put(SKIP, !gotLock);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    grantDao.unlockGrant(grantId, context.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
