package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.gcp.CreateGcpContextFlightV2;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final TpsApiDispatch tpsApiDispatch;

  @Autowired
  public GcpCloudContextService(
      WorkspaceDao workspaceDao, SamService samService, TpsApiDispatch tpsApiDispatch) {
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  /**
   * Create an empty GCP cloud context in the database for a workspace. Supports {@link
   * CreateGcpContextFlightV2} This is designed for use in the createGcpContext flight and assumes
   * that a later step will call {@link #createGcpCloudContextFinish}.
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
   * Update the GCP cloud context.
   *
   * <p>Retrieve the original GCP context, and then accordingly update it.
   *
   * <p>Note: Updates to the {@code gcpDefaultZone} in the cloud context will be synced with the
   * workspace properties until both the UI and CLI have migrated away from using properties (i.e.,
   * when PF-2556 is resolved):
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @param gcpDefaultZone The default zone for all newly-created GCP resources in this workspace.
   */
  public void updateGcpCloudContext(
      TpsApiDispatch tpsApiDispatch,
      UUID workspaceUuid,
      String gcpDefaultZone,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    // Get the required GCP context.
    GcpCloudContext gcpCloudContext = getRequiredGcpCloudContext(workspaceUuid, userRequest);

    // Add defaultZone onto the GCP context object
    Map<String, String> propertySyncUpdate = new HashMap<>();
    if (gcpDefaultZone != null) {
      // Validate the region against the workspace policy.
      ResourceValidationUtils.validateGcpRegion(
          tpsApiDispatch, workspaceUuid, GcpUtils.parseRegion(gcpDefaultZone));
      gcpCloudContext.setGcpDefaultZone(gcpDefaultZone);
      // TODO (PF-2556): Remove once terra-default-location workspace properties have been
      // deprecated.
      propertySyncUpdate.put(
          WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION, gcpDefaultZone);
    }
    // Call workspace dao to update the cloud context
    workspaceDao.updateCloudContext(workspaceUuid, CloudPlatform.GCP, gcpCloudContext.serialize());
    // TODO (PF-2556): Remove once terra-default-location workspace properties have been deprecated.
    // Sync updates to the workspace properties until both the UI and CLI have migrated.
    workspaceDao.updateWorkspaceProperties(workspaceUuid, propertySyncUpdate);
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
}
