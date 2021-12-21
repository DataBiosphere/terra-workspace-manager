package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
   * Create an empty GCP cloud context in the database for a workspace. Supports {@link
   * bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2} This is designed for use
   * in the createGcpContext flight and assumes that a later step will call {@link
   * #createAzureCloudContextFinish}.
   *
   * @param workspaceId workspace id where the context is being created
   * @param flightId flight doing the creating
   */
  public void createAzureCloudContextStart(UUID workspaceId, String flightId) {
    workspaceDao.createCloudContextStart(workspaceId, CloudPlatform.AZURE, flightId);
  }

  /**
   * Complete creation of the Azure cloud context by filling in the context attributes. This is
   * designed for use in createAzureContext flight and assumes that an earlier step has called
   * {@link #createAzureCloudContextStart}.
   *
   * @param workspaceId workspace id of the context
   * @param cloudContext cloud context data
   * @param flightId flight completing the creation
   */
  public void createAzureCloudContextFinish(
      UUID workspaceId, AzureCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContextFinish(
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
   * WorkspaceDao#deleteCloudContextWithFlightIdValidation(UUID, CloudPlatform, String)}
   *
   * @param workspaceId workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  public void deleteAzureCloudContextWithFlightIdValidation(UUID workspaceId, String flightId) {
    workspaceDao.deleteCloudContextWithFlightIdValidation(
        workspaceId, CloudPlatform.AZURE, flightId);
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
}
