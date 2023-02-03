package bio.terra.workspace.service.grant.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.grant.GrantData;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static java.lang.Boolean.TRUE;

public class RevokeTemporaryGrantFlight extends Flight {
  public static final String SKIP = "skip";

  public static final String GRANT_ID_KEY = "grantId";

  public RevokeTemporaryGrantFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    var dbRetry = RetryRules.shortDatabase();

    UUID grantId = inputParameters.get(GRANT_ID_KEY, UUID.class);

    addStep(new LockGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
    // output of the step in the working map is:
    //  skip - if the lock failed, skip Boolean is set true and the steps do nothing.

    addStep(new RevokeStep(
      flightBeanBag.getGcpCloudContextService(),
      flightBeanBag.getCrlService(),
      flightBeanBag.getGrantDao(),
      grantId));

    addStep(new DeleteGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
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
      // Try to lock. If we fail, we skip the rest of the flight.
      // Failing would indicate either the grant is gone (revoked by another flight)
      // or it is locked by another flight.
      boolean gotLock = grantDao.lockGrant(grantId, context.getFlightId());
      context.getWorkingMap().put(SKIP, gotLock);
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
    private final GcpCloudContextService gcpCloudContextService;
    private final CrlService crlService;
    private final GrantDao grantDao;
    private final UUID grantId;
    public RevokeStep(
      GcpCloudContextService gcpCloudContextService,
      CrlService crlService,
      GrantDao grantDao,
      UUID grantId) {
      this.gcpCloudContextService = gcpCloudContextService;
      this.crlService = crlService;
      this.grantDao = grantDao;
      this.grantId = grantId;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      Boolean skip = context.getWorkingMap().get(SKIP, Boolean.class);
      if (skip == null || skip.equals(TRUE)) {
        return StepResult.getStepResultSuccess();
      }

      // Get the grant data - we locked it, so if it is not there something is wrong
      GrantData grantData = grantDao.getGrant(grantId);
      if (grantData == null) {
        throw new InternalLogicException("Locked grant not found: " + grantId);
      }

      switch (grantData.grantType()) {
        case RESOURCE -> {
          // TODO
        }
        case PROJECT -> {
          try {
            CloudResourceManagerCow resourceManagerCow = crlService.getCloudResourceManagerCow();

            Optional<String> gcpProjectId = gcpCloudContextService.getGcpProject(grantData.workspaceId());
            // Tolerate the workspace or cloud context being gone
            if (gcpProjectId.isPresent()) {
              Policy policy =
                resourceManagerCow
                  .projects()
                  .getIamPolicy(gcpProjectId.get(), new GetIamPolicyRequest())
                  .execute();
              // Tolerate no bindings
              if (policy.getBindings() != null) {
                for (Binding binding : policy.getBindings()) {
                  if (binding.getRole().equals(grantData.role())) {
                    binding.getMembers().remove(grantData.userMember());
                    binding.getMembers().remove(grantData.petSaMember());
                  }
                }
                SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(policy);
                resourceManagerCow
                  .projects()
                  .setIamPolicy(gcpProjectId.get(), request)
                  .execute();
              }
            }
          } catch (IOException e) {
            throw new RetryException("Retry get policy", e);
          }
        }
        case ACT_AS -> {
          // TODO
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
