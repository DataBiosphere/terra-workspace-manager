package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing Azure cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AzureCloudContextService {

  private final WorkspaceDao workspaceDao;

  @Autowired
  public AzureCloudContextService(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  /**
   * Create the GCP cloud context of the workspace
   *
   * @param workspaceId unique id of the workspace
   * @param cloudContext the Azure cloud context to create
   */
  public void createAzureCloudContext(
      UUID workspaceId, AzureCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContext(
        workspaceId, CloudPlatform.AZURE, cloudContext.serialize(), flightId);
  }

  /**
   * Delete the Azure cloud context for a workspace For details: {@link
   * WorkspaceDao#deleteCloudContext(UUID, CloudPlatform)}
   *
   * @param workspaceId workspace of the cloud context
   */
  public void deleteAzureCloudContext(UUID workspaceId) {
    workspaceDao.deleteCloudContext(workspaceId, CloudPlatform.AZURE);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithCheck(UUID, CloudPlatform, String)}
   *
   * @param workspaceId workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  public void deleteAzureCloudContextWithCheck(UUID workspaceId, String flightId) {
    workspaceDao.deleteCloudContextWithCheck(workspaceId, CloudPlatform.AZURE, flightId);
  }

  /**
   * Retrieve the optional GCP cloud context
   *
   * @param workspaceId workspace identifier of the cloud context
   * @return optional GCP cloud context
   */
  public Optional<AzureCloudContext> getAzureCloudContext(UUID workspaceId) {
    return workspaceDao
        .getCloudContext(workspaceId, CloudPlatform.AZURE)
        .map(AzureCloudContext::deserialize);
  }

  /**
   * Retrieve the optional Azure cloud context, providing a workspace This is a frequent usage, so
   * we make a method for it to save coding the fetch of workspace id every time
   */
  public Optional<AzureCloudContext> getAzureCloudContext(Workspace workspace) {
    return getAzureCloudContext(workspace.getWorkspaceId());
  }
}
