package bio.terra.workspace.service.grant.flight;

import bio.terra.common.exception.NotImplementedException;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.service.grant.GrantData;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.flight.RetrieveGcpResourcesRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceWithoutRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateResourcesRegionStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

import java.util.Optional;
import java.util.UUID;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IS_WET_RUN;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class RevokeTemporaryGrantFlight extends Flight {
  public static final String GRANT_DATA_KEY = "grantData";
  public static final String SKIP = "skip";

  public static final String GRANT_ID_KEY = "grantId";

  public RevokeTemporaryGrantFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    var dbRetry = RetryRules.shortDatabase();

    UUID grantId = inputParameters.get(GRANT_ID_KEY, UUID.class);

    addStep(new LockGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
    // output of the step in the working map is:
    //  grantData - if locked and retrieved, the grant data object
    //  skip - if the lock/retrieve failed, skip Boolean is set true and the steps do nothing.

    addStep(new RevokeStep());

    addStep(new DeleteGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry)
  }


  /**
   * Step 1: lock the grant row and return the grant data
   *   On undo, unlock the grant row if it exists and this flight has the lock.
   */
  public static class LockGrantStep implements Step {
    private final GrantDao grantDao;
    private final UUID grantId;

    public LockGrantStep(GrantDao grantDao, UUID grantId) {
      this.grantDao = grantDao;
      this.grantId = grantId;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      Optional<GrantData> grantData = grantDao.lockAndGetGrant(grantId);
      if (grantData.isEmpty()) {
        context.getWorkingMap().put(SKIP, TRUE);
      } else {
        context.getWorkingMap().put(SKIP, FALSE);
        context.getWorkingMap().put(GRANT_DATA_KEY, grantData);
      }
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      grantDao.unlockGrant(grantId, context.getFlightId());
      return StepResult.getStepResultSuccess();
    }
  }

  /**
   * Step 2: revoke the permission
   */
  public static class RevokeStep implements Step {
    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      Boolean skip = context.getWorkingMap().get(SKIP, Boolean.class);
      if (skip == null || skip.equals(TRUE)) {
        return StepResult.getStepResultSuccess();
      }

      GrantData grantData = context.getWorkingMap().get(GRANT_DATA_KEY, GrantData.class);

      switch (grantData.grantType()) {
        case RESOURCE -> {
        }
        case PROJECT -> {
        }
        case ACT_AS -> {
        }
      }

      // <<<!!! YOU ARE HERE >>>
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }
  }

  /**
   * Step 3: delete the grant row
   */
  public static class DeleteGrantStep implements Step {
    private final GrantDao grantDao;
    private final UUID grantId;

    public DeleteGrantStep(GrantDao grantDao, UUID grantId) {
      this.grantDao = grantDao;
      this.grantId = grantId;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      grantDao.deleteGrant(grantId, context.getFlightId());
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      // No undo possible if the delete fails. Dismal failure. Requires a human
      // to look at the DB and see what is up with the grant.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException("Possible corruption of grant id" + grantId));
    }
  }


}
