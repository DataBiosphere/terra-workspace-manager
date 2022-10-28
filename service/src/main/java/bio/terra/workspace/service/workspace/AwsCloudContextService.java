package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AwsCloudContextService {

  private final WorkspaceDao workspaceDao;

  @Autowired
  public AwsCloudContextService(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  /**
   * Create an empty AWS cloud context in the database for a workspace. Supports {@link
   * bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2} This is designed for use
   * in the createGcpContext flight and assumes that a later step will call {@link
   * #createAwsCloudContextFinish}.
   *
   * @param workspaceUuid workspace id where the context is being created
   * @param flightId flight doing the creating
   */
  public void createAwsCloudContextStart(UUID workspaceUuid, String flightId) {
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.AWS, flightId);
  }

  /**
   * Complete creation of the AWS cloud context by filling in the context attributes. This is
   * designed for use in createAwsContext flight and assumes that an earlier step has called {@link
   * #createAwsCloudContextStart}.
   *
   * @param workspaceUuid workspace id of the context
   * @param cloudContext cloud context data
   * @param flightId flight completing the creation
   */
  public void createAwsCloudContextFinish(
      UUID workspaceUuid, AwsCloudContext cloudContext, String flightId) {
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.AWS, cloudContext.serialize(), flightId);
  }

  /**
   * Delete the AWS cloud context for a workspace For details: {@link
   * WorkspaceDao#deleteCloudContext(UUID, CloudPlatform)}
   *
   * @param workspaceUuid workspace of the cloud context
   */
  public void deleteAwsCloudContext(UUID workspaceUuid) {
    workspaceDao.deleteCloudContext(workspaceUuid, CloudPlatform.AWS);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. For details: {@link
   * WorkspaceDao#deleteCloudContextWithFlightIdValidation(UUID, CloudPlatform, String)}
   *
   * @param workspaceUuid workspace of the cloud context
   * @param flightId flight id making the delete request
   */
  public void deleteAwsCloudContextWithFlightIdValidation(UUID workspaceUuid, String flightId) {
    workspaceDao.deleteCloudContextWithFlightIdValidation(
        workspaceUuid, CloudPlatform.AZURE, flightId);
  }

  /**
   * Retrieve the optional AWS cloud context
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return optional AWS cloud context
   */
  @Traced
  public Optional<AwsCloudContext> getAwsCloudContext(UUID workspaceUuid) {
    return workspaceDao
        .getCloudContext(workspaceUuid, CloudPlatform.AWS)
        .map(AwsCloudContext::deserialize);
  }

  public AwsCloudContext getRequiredAwsCloudContext(UUID workspaceUuid) {
    return getAwsCloudContext(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires AWS cloud context"));
  }
}
