package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opencensus.contrib.spring.aop.Traced;
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
   * Create an empty Azure cloud context in the database for a workspace. This is designed for use
   * in the CreateDbAzureCloudContextStartStep flight and assumes that a later step will call {@link
   * #createAzureCloudContextFinish}.
   *
   * @param workspaceUuid workspace id where the context is being created
   * @param flightId flight doing the creating
   */
  public void createAzureCloudContextStart(UUID workspaceUuid, String flightId) {
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.AZURE, flightId);
  }

  /**
   * Complete creation of the Azure cloud context by filling in the context attributes. This is
   * designed for use in createAzureContext flight and assumes that an earlier step has called
   * {@link #createAzureCloudContextStart}.
   *
   * @param workspaceUuid workspace id of the context
   * @param cloudContext cloud context data
   * @param flightId flight completing the creation
   */
  public void createAzureCloudContextFinish(
      UUID workspaceUuid, AzureCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.AZURE, cloudContext.serialize(), flightId);
  }

  /**
   * Delete the Azure cloud context for a workspace For details: {@link
   * WorkspaceDao#deleteCloudContext(UUID, CloudPlatform)}
   *
   * @param workspaceUuid workspace of the cloud context
   */
  public void deleteAzureCloudContext(UUID workspaceUuid) {
    workspaceDao.deleteCloudContext(workspaceUuid, CloudPlatform.AZURE);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithFlightIdValidation(UUID, CloudPlatform, String)}
   *
   * @param workspaceUuid workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  public void deleteAzureCloudContextWithFlightIdValidation(UUID workspaceUuid, String flightId) {
    workspaceDao.deleteCloudContextWithFlightIdValidation(
        workspaceUuid, CloudPlatform.AZURE, flightId);
  }

  /**
   * Retrieve the optional Azure cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional Azure cloud context
   */
  @Traced
  public Optional<AzureCloudContext> getAzureCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.AZURE)
        .map(AzureCloudContext::deserialize);
  }

  public AzureCloudContext getRequiredAzureCloudContext(UUID workspaceUuid) {
    return getAzureCloudContext(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires Azure cloud context"));
  }
}
