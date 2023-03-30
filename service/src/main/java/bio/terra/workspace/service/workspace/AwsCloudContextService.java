package bio.terra.workspace.service.workspace;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.*;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service provides methods for managing AWS cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AwsCloudContextService {

  private final WorkspaceDao workspaceDao;
  private final AwsConfiguration awsConfiguration;
  private final Environment environment;

  @Autowired
  public AwsCloudContextService(WorkspaceDao workspaceDao, AwsConfiguration awsConfiguration)
      throws IOException {
    this.workspaceDao = workspaceDao;
    this.awsConfiguration = awsConfiguration;
    this.environment = AwsUtils.createEnvironmentDiscovery(awsConfiguration).discoverEnvironment();
  }

  /**
   * Create an empty AWS cloud context in the database for a workspace. This is designed for use in
   * the CreateDbAwsCloudContextStartStep flight and assumes that a later step will call {@link
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
        workspaceUuid, CloudPlatform.AWS, flightId);
  }

  /**
   * Retrieve the optional AWS cloud context for given workspace
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

  /**
   * Retrieve the required AWS cloud context for given workspace
   *
   * @param workspaceUuid workspace identifier of the cloud context
   * @return AWS cloud context
   * @throws CloudContextRequiredException CloudContextRequiredException
   */
  public AwsCloudContext getRequiredAwsCloudContext(UUID workspaceUuid) {
    return getAwsCloudContext(workspaceUuid)
        .orElseThrow(
            () -> new CloudContextRequiredException("Operation requires AWS cloud context"));
  }

  /**
   * Return the AWS cloud context for current environment
   *
   * @return AWS cloud context
   */
  public @NotNull AwsCloudContext fromConfiguration() {
    Metadata metadata = this.environment.getMetadata();
    return new AwsCloudContext(
        metadata.getOrganizationId(),
        metadata.getAccountId(),
        metadata.getTenantAlias(),
        metadata.getEnvironmentAlias());
  }
}
