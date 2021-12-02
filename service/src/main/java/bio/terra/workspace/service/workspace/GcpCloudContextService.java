package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.common.annotations.VisibleForTesting;
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
   * Create the GCP cloud context of the workspace
   *
   * @param workspaceId unique id of the workspace
   * @param cloudContext the GCP cloud context to create
   */
  @Deprecated // TODO: PF-1238 remove
  public void createGcpCloudContext(
      UUID workspaceId, GcpCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContext(
        workspaceId, CloudPlatform.GCP, cloudContext.serialize(), flightId);
  }

  /**
   * Create and lock the GCP cloud context for a workspace Supports {@link
   * bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2}
   *
   * @param workspaceId workspace id where the context is being created
   * @param flightId flight doing the creating
   */
  public void createAndLockGcpCloudContext(UUID workspaceId, String flightId) {
    workspaceDao.createAndLockCloudContext(workspaceId, CloudPlatform.GCP, flightId);
  }

  /**
   * Update and unlock the GCP cloud context for a workspace. Store the cloud context data
   *
   * @param workspaceId workspace id of the context
   * @param cloudContext cloud context data
   * @param flightId flight completing the creation
   */
  public void updateAndUnlockGcpCloudContext(
      UUID workspaceId, GcpCloudContext cloudContext, String flightId) {
    workspaceDao.updateAndUnlockCloudContext(
        workspaceId, CloudPlatform.GCP, cloudContext.serialize(), flightId);
  }

  /**
   * Delete the GCP cloud context for a workspace For details: {@link
   * WorkspaceDao#deleteCloudContext(UUID, CloudPlatform)}
   *
   * @param workspaceId workspace of the cloud context
   */
  public void deleteGcpCloudContext(UUID workspaceId) {
    workspaceDao.deleteCloudContext(workspaceId, CloudPlatform.GCP);
  }

  /**
   * Delete a locked GCP cloud context for a workspace. Used in the create context code during undo
   * to remove a cloud context, but only if we have it locked.
   *
   * @param workspaceId workspace id of the context
   * @param flightId locking flight id
   */
  public void deleteAndUnlockGcpCloudContext(UUID workspaceId, String flightId) {
    workspaceDao.deleteAndUnlockCloudContext(workspaceId, CloudPlatform.GCP, flightId);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithCheck(UUID, CloudPlatform, String)}
   *
   * @param workspaceId workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  @Deprecated // TODO: PF-1238 remove
  public void deleteGcpCloudContextWithCheck(UUID workspaceId, String flightId) {
    workspaceDao.deleteCloudContextWithCheck(workspaceId, CloudPlatform.GCP, flightId);
  }

  /**
   * Retrieve the optional GCP cloud context
   *
   * @param workspaceId workspace identifier of the cloud context
   * @return optional GCP cloud context
   */
  public Optional<GcpCloudContext> getGcpCloudContext(UUID workspaceId) {
    return workspaceDao
        .getCloudContext(workspaceId, CloudPlatform.GCP)
        .map(GcpCloudContext::deserialize);
  }

  /**
   * Retrieve the GCP cloud context. If it does not have the policies filled in, retrieve the
   * policies from Sam, fill them in, and update the cloud context.
   *
   * <p>This is used during controlled resource create. Since the caller may not have permission to
   * read the workspace policies, we use the WSM SA to query Sam.
   *
   * @param workspaceId workspace identifier of the cloud context
   * @return GCP cloud context with all policies filled in.
   */
  public GcpCloudContext getRequiredGcpCloudContext(UUID workspaceId) throws InterruptedException {
    GcpCloudContext context =
        getGcpCloudContext(workspaceId)
            .orElseThrow(
                () -> new CloudContextRequiredException("Operation requires GCP cloud context"));

    // policyOwner is a good sentinel for knowing we need to update the cloud context and
    // store the sync'd workspace policies.
    if (context.getSamPolicyOwner().isEmpty()) {
      context.setSamPolicyOwner(samService.getWorkspacePolicyAsWsm(workspaceId, WsmIamRole.OWNER));
      context.setSamPolicyWriter(
          samService.getWorkspacePolicyAsWsm(workspaceId, WsmIamRole.WRITER));
      context.setSamPolicyReader(
          samService.getWorkspacePolicyAsWsm(workspaceId, WsmIamRole.READER));
      context.setSamPolicyApplication(
          samService.getWorkspacePolicyAsWsm(workspaceId, WsmIamRole.APPLICATION));
    }
    workspaceDao.updateCloudContext(workspaceId, CloudPlatform.GCP, context.serialize());
    return context;
  }

  /**
   * Retrieve the flight ID that created the GCP cloud context for a given workspace, if that cloud
   * context exists.
   */
  @Deprecated // TODO: PF-1238 remove
  public Optional<String> getGcpCloudContextFlightId(UUID workspaceId) {
    return workspaceDao.getCloudContextFlightId(workspaceId, CloudPlatform.GCP);
  }

  /**
   * Retrieve the optional GCP cloud context, providing a workspace. This is a frequent usage, so we
   * make a method for it to save coding the fetch of workspace id every time
   */
  @VisibleForTesting
  public Optional<GcpCloudContext> getGcpCloudContext(Workspace workspace) {
    return getGcpCloudContext(workspace.getWorkspaceId());
  }

  /**
   * Helper method for looking up the GCP project ID for a given workspace ID, if one exists. Unlike
   * {@link #getRequiredGcpProject(UUID)}, this returns an empty Optional instead of throwing if the
   * given workspace does not have a GCP cloud context. NOTE: no user auth validation
   *
   * @param workspaceId workspace identifier of the cloud context
   * @return optional GCP project from the cloud context
   */
  public Optional<String> getGcpProject(UUID workspaceId) {
    return getGcpCloudContext(workspaceId).map(GcpCloudContext::getGcpProjectId);
  }

  /**
   * Helper method used by other classes that require the GCP project to exist in the workspace. It
   * throws if the project (GCP cloud context) is not set up. NOTE: no user auth validation
   *
   * @param workspaceId unique workspace id
   * @return GCP project id
   */
  public String getRequiredGcpProject(UUID workspaceId) {
    return getGcpProject(workspaceId)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires GCP cloud context"));
  }
}
