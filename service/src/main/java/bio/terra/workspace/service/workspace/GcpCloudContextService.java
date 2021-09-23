package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing GCP cloud context. These methods do not perform any
 * access control and operate directly against the {@link bio.terra.workspace.db.WorkspaceDao}
 */
@Component
public class GcpCloudContextService {

  private final WorkspaceDao workspaceDao;

  @Autowired
  public GcpCloudContextService(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  /**
   * Create the GCP cloud context of the workspace
   *
   * @param workspaceId unique id of the workspace
   * @param cloudContext the GCP cloud context to create
   */
  public void createGcpCloudContext(
      UUID workspaceId, GcpCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContext(
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
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithCheck(UUID, CloudPlatform, String)}
   *
   * @param workspaceId workspace of the cloud context
   * @param flightId flight id making the delete request
   */
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
   * Retrieve the optional GCP cloud context, providing a workspace This is a frequent usage, so we
   * make a method for it to save coding the fetch of workspace id every time
   */
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
