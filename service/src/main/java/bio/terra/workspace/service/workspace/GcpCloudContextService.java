package bio.terra.workspace.service.workspace;

import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.common.utils.WsmFlight;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.CheckSpendProfileStep;
import bio.terra.workspace.service.workspace.flight.CreateCustomGcpRolesStep;
import bio.terra.workspace.service.workspace.flight.CreateDbGcpCloudContextStep;
import bio.terra.workspace.service.workspace.flight.CreatePetSaStep;
import bio.terra.workspace.service.workspace.flight.GcpCloudSyncStep;
import bio.terra.workspace.service.workspace.flight.GrantWsmRoleAdminStep;
import bio.terra.workspace.service.workspace.flight.PullProjectFromPoolStep;
import bio.terra.workspace.service.workspace.flight.SetProjectBillingStep;
import bio.terra.workspace.service.workspace.flight.SyncSamGroupsStep;
import bio.terra.workspace.service.workspace.flight.UpdateDbGcpCloudContextStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing GCP cloud context in the WSM database. These methods
 * do not perform any access control and operate directly against the {@link
 * bio.terra.workspace.db.WorkspaceDao}
 */
@Component
public class GcpCloudContextService {

  private final WorkspaceDao workspaceDao;
  private final SamService samService;

  @Autowired
  public GcpCloudContextService(WorkspaceDao workspaceDao, SamService samService) {
    this.workspaceDao = workspaceDao;
    this.samService = samService;
  }

  /**
   * Create an empty GCP cloud context in the database for a workspace. Supports {@link
   * bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2} This is designed for use
   * in the createGcpContext flight and assumes that a later step will call {@link
   * #createGcpCloudContextFinish}.
   *
   * @param workspaceUuid workspace id where the context is being created
   * @param flightId flight doing the creating
   */
  public void createGcpCloudContextStart(UUID workspaceUuid, String flightId) {
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
  }

  /**
   * Complete creation of the GCP cloud context by filling in the context attributes. This is
   * designed for use in createGcpContext flight and assumes that an earlier step has called {@link
   * #createGcpCloudContextStart}.
   *
   * @param workspaceUuid workspace id of the context
   * @param cloudContext cloud context data
   * @param flightId flight completing the creation
   */
  public void createGcpCloudContextFinish(
      UUID workspaceUuid, GcpCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.GCP, cloudContext.serialize(), flightId);
  }

  /**
   * Delete the GCP cloud context for a workspace For details: {@link
   * WorkspaceDao#deleteCloudContext(UUID, CloudPlatform)}
   *
   * @param workspaceUuid workspace of the cloud context
   */
  public void deleteGcpCloudContext(UUID workspaceUuid) {
    workspaceDao.deleteCloudContext(workspaceUuid, CloudPlatform.GCP);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithFlightIdValidation(UUID, CloudPlatform, String)}
   *
   * @param workspaceUuid workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  public void deleteGcpCloudContextWithCheck(UUID workspaceUuid, String flightId) {
    workspaceDao.deleteCloudContextWithFlightIdValidation(
        workspaceUuid, CloudPlatform.GCP, flightId);
  }

  /**
   * Retrieve the optional GCP cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional GCP cloud context
   */
  @Traced
  public Optional<GcpCloudContext> getGcpCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.GCP)
        .map(GcpCloudContext::deserialize);
  }

  /**
   * Retrieve the GCP cloud context. If it does not have the policies filled in, retrieve the
   * policies from Sam, fill them in, and update the cloud context.
   *
   * <p>This is used during controlled resource create. Since the caller may not have permission to
   * read the workspace policies, we use the WSM SA to query Sam.
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return GCP cloud context with all policies filled in.
   */
  public GcpCloudContext getRequiredGcpCloudContext(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) throws InterruptedException {
    GcpCloudContext context =
        getGcpCloudContext(workspaceUuid)
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires GCP cloud context"));

    // TODO(PF-1666): Remove this once we've migrated off GcpCloudContext (V1).
    // policyOwner is a good sentinel for knowing we need to update the cloud context and
    // store the sync'd workspace policies.
    if (context.getSamPolicyOwner().isEmpty()) {
      context.setSamPolicyOwner(
          samService.getWorkspacePolicy(workspaceUuid, WsmIamRole.OWNER, userRequest));
      context.setSamPolicyWriter(
          samService.getWorkspacePolicy(workspaceUuid, WsmIamRole.WRITER, userRequest));
      context.setSamPolicyReader(
          samService.getWorkspacePolicy(workspaceUuid, WsmIamRole.READER, userRequest));
      context.setSamPolicyApplication(
          samService.getWorkspacePolicy(workspaceUuid, WsmIamRole.APPLICATION, userRequest));
      workspaceDao.updateCloudContext(workspaceUuid, CloudPlatform.GCP, context.serialize());
    }
    return context;
  }

  /**
   * Helper method for looking up the GCP project ID for a given workspace ID, if one exists. Unlike
   * {@link #getRequiredGcpProject(UUID)}, this returns an empty Optional instead of throwing if the
   * given workspace does not have a GCP cloud context. NOTE: no user auth validation
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional GCP project from the cloud context
   */
  public Optional<String> getGcpProject(UUID workspaceUuid) {
    return getGcpCloudContext(workspaceUuid).map(GcpCloudContext::getGcpProjectId);
  }

  /**
   * Helper method used by other classes that require the GCP project to exist in the workspace. It
   * throws if the project (GCP cloud context) is not set up. NOTE: no user auth validation
   *
   * @param workspaceUuid unique workspace id
   * @return GCP project id
   */
  public String getRequiredGcpProject(UUID workspaceUuid) {
    return getGcpProject(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires GCP cloud context"));
  }

  /**
   * Generate the steps to create a GCP cloud context. This set of steps is used in the standalone
   * create GCP cloud context flight and in clone workspace.
   *
   * @param flight flight to add steps to
   * @param workspaceUuid workspace in which we are creating the cloud context
   * @param userRequest user credentials
   */
  public void makeCreateGcpContextSteps(
      WsmFlight flight, UUID workspaceUuid, AuthenticatedUserRequest userRequest) {

    CrlService crl = flight.beanBag().getCrlService();

    RetryRule shortRetry = RetryRules.shortExponential();
    RetryRule cloudRetry = RetryRules.cloud();

    // Check that we are allowed to spend money. No point doing anything else unless that
    // is true.
    flight.addStep(
        new CheckSpendProfileStep(
            flight.beanBag().getWorkspaceDao(),
            flight.beanBag().getSpendProfileService(),
            workspaceUuid,
            userRequest,
            CloudPlatform.GCP,
            flight.beanBag().getFeatureConfiguration().isBpmGcpEnabled()));

    // Write the cloud context row in a "locked" state
    flight.addStep(
        new CreateDbGcpCloudContextStep(
            workspaceUuid, flight.beanBag().getGcpCloudContextService()),
        shortRetry);

    // Allocate the GCP project from RBS. We derive the rbsRequestId from the workspaceUuid.
    // That makes it repeatable and unique without having to generate another id.
    String rbsRequestId = "wm-" + workspaceUuid.toString().replaceAll("-", "");

    flight.addStep(
        new PullProjectFromPoolStep(
            flight.beanBag().getBufferService(), crl.getCloudResourceManagerCow(), rbsRequestId),
        RetryRules.buffer());

    // Configure the project for WSM
    flight.addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()), cloudRetry);
    flight.addStep(new GrantWsmRoleAdminStep(crl), shortRetry);
    flight.addStep(new CreateCustomGcpRolesStep(crl.getIamCow()), shortRetry);
    flight.addStep(
        new SyncSamGroupsStep(flight.beanBag().getSamService(), workspaceUuid, userRequest),
        shortRetry);
    flight.addStep(new GcpCloudSyncStep(crl.getCloudResourceManagerCow()), cloudRetry);
    flight.addStep(new CreatePetSaStep(flight.beanBag().getSamService(), userRequest), shortRetry);

    // Store the cloud context data and unlock the database row
    // This must be the last step, since it clears the lock. So this step also
    // sets the flight response.
    flight.addStep(
        new UpdateDbGcpCloudContextStep(
            workspaceUuid, flight.beanBag().getGcpCloudContextService()),
        shortRetry);
  }
}
