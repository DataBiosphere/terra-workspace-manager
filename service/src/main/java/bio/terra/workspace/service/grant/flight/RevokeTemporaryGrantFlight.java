package bio.terra.workspace.service.grant.flight;

import static java.lang.Boolean.TRUE;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.storage.StorageCow;
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
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This module contains the flight and all of the steps, to minimize the footprint of this
 * workaround. There are three steps. The revoke is complex, so the module is sequenced:
 *
 * <ul>
 *   <le> step 1 - lock the grant</le> <le> step 3 - unlock the grant</le> <le> step 2 and all of
 *   its subroutines - revoke the grant</le>
 * </ul>
 */
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

    addStep(
        new RevokeStep(
            flightBeanBag.getGcpCloudContextService(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGrantDao(),
            flightBeanBag.getControlledResourceService(),
            grantId));

    addStep(new DeleteGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
  }

  /**
   * Step 1: lock the grant row and return the grant data On undo, unlock the grant row if it exists
   * and this flight has the lock.
   */
  public static class LockGrantStep implements Step {
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
      logger.info("Result of lock is: {}", gotLock);
      context.getWorkingMap().put(SKIP, !gotLock);
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      grantDao.unlockGrant(grantId, context.getFlightId());
      return StepResult.getStepResultSuccess();
    }
  }

  /** Step 3: delete the grant row */
  public static class DeleteGrantStep implements Step {
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
      if (skip == null || skip.equals(TRUE)) {
        logger.info("Skipping delete grant");
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

  /**
   * Step 2: revoke the permission A step that illustrates the consistent approach to IAM in GCP
   * (not!)
   */
  public static class RevokeStep implements Step {
    public static Logger logger = LoggerFactory.getLogger(RevokeStep.class);
    private final GcpCloudContextService gcpCloudContextService;
    private final CrlService crlService;
    private final GrantDao grantDao;
    private final ControlledResourceService controlledResourceService;
    private final UUID grantId;

    public RevokeStep(
        GcpCloudContextService gcpCloudContextService,
        CrlService crlService,
        GrantDao grantDao,
        ControlledResourceService controlledResourceService,
        UUID grantId) {
      this.gcpCloudContextService = gcpCloudContextService;
      this.crlService = crlService;
      this.grantDao = grantDao;
      this.controlledResourceService = controlledResourceService;
      this.grantId = grantId;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      Boolean skip = context.getWorkingMap().get(SKIP, Boolean.class);
      if (skip == null || skip.equals(TRUE)) {
        logger.info("Skipping revoke");
        return StepResult.getStepResultSuccess();
      }

      // Get the grant data - we locked it, so if it is not there something is wrong
      GrantData grantData = grantDao.getGrant(grantId);
      if (grantData == null) {
        throw new InternalLogicException("Locked grant not found: " + grantId);
      }

      logger.info("Revoking grant {} type {}", grantData.grantId(), grantData.grantType());
      switch (grantData.grantType()) {
        case RESOURCE -> revokeResource(grantData);
        case PROJECT -> revokeProject(grantData);
        case ACT_AS -> revokeActAs(grantData);
      }

      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }

    private void revokeProject(GrantData grantData) {
      try {
        CloudResourceManagerCow resourceManagerCow = crlService.getCloudResourceManagerCow();

        Optional<String> gcpProjectId =
            gcpCloudContextService.getGcpProject(grantData.workspaceId());
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
            resourceManagerCow.projects().setIamPolicy(gcpProjectId.get(), request).execute();
          }
        }
      } catch (IOException e) {
        throw new RetryException("Retry get policy", e);
      }
    }

    private void revokeResource(GrantData grantData) {
      ControlledResource controlledResource =
          controlledResourceService.getControlledResource(
              grantData.workspaceId(), grantData.resourceId());
      logger.info(
          "Found resource {} of type {}",
          controlledResource.getResourceId(),
          controlledResource.getResourceType());

      // If this were permanent, I would put the revoke logic in each controlled resource.
      // To keep it to a small number of files, we use a switch.
      switch (controlledResource.getResourceType()) {
        case CONTROLLED_GCP_GCS_BUCKET -> {
          ControlledGcsBucketResource bucketResource =
              controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
          revokeResourceBucket(bucketResource, grantData);
        }
        case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> {
          ControlledAiNotebookInstanceResource notebookResource =
              controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
          revokeResourceNotebook(notebookResource, grantData);
        }
        case CONTROLLED_GCP_BIG_QUERY_DATASET -> {
          ControlledBigQueryDatasetResource bqResource =
              controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
          revokeResourceBq(bqResource, grantData);
        }
        default -> {
          throw new InternalLogicException("Non-GCP resource got a temporary grant");
        }
      }
    }

    private void revokeResourceBq(
        ControlledBigQueryDatasetResource bqResource, GrantData grantData) {
      // Not implemented yet - just let it go through
    }

    private void revokeResourceBucket(
        ControlledGcsBucketResource bucketResource, GrantData grantData) {
      String gcpProjectId = gcpCloudContextService.getRequiredGcpProject(grantData.workspaceId());
      logger.info("Revoke bucket {} in project {}", bucketResource.getBucketName(), gcpProjectId);

      StorageCow wsmSaStorageCow = crlService.createStorageCow(gcpProjectId);
      com.google.cloud.Policy policy = wsmSaStorageCow.getIamPolicy(bucketResource.getBucketName());

      if (policy.getBindingsList() != null) {
        // getBindingsList() returns an ImmutableList and copying over to an ArrayList so it's
        // mutable.
        List<com.google.cloud.Binding> bindings = new ArrayList<>(policy.getBindingsList());
        // Remove role-member
        for (int index = 0; index < bindings.size(); index++) {
          com.google.cloud.Binding binding = bindings.get(index);
          logger.info(
              "Check: binding role {} == grant role {}", binding.getRole(), grantData.role());
          if (binding.getRole().equals(grantData.role())) {
            logger.info(
                "MATCH: binding role {} == grant role {}", binding.getRole(), grantData.role());
            bindings.set(
                index,
                binding.toBuilder()
                    .removeMembers(grantData.petSaMember(), grantData.userMember())
                    .build());
            break;
          }
        }

        // Update policy to remove members
        com.google.cloud.Policy.Builder updatedPolicyBuilder = policy.toBuilder();
        updatedPolicyBuilder.setBindings(bindings).setVersion(3);
        com.google.cloud.Policy updatedPolicy =
            wsmSaStorageCow.setIamPolicy(
                bucketResource.getBucketName(), updatedPolicyBuilder.build());
        logger.info("Updated policy is: {}", updatedPolicy);
      }
    }

    private void revokeResourceNotebook(
        ControlledAiNotebookInstanceResource notebookResource, GrantData grantData) {
      // Not implemented yet - just let it go through
    }

    private void revokeActAs(GrantData grantData) {
      // Not implemented yet - just let it go through
    }
  }
}
