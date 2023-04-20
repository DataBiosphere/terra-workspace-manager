package bio.terra.workspace.service.workspace;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.Authentication;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;

/**
 * This service provides methods for managing AWS cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AwsCloudContextService {
  private final AwsConfiguration awsConfiguration;
  private final WorkspaceDao workspaceDao;
  private final FeatureService featureService;

  private EnvironmentDiscovery environmentDiscovery;

  @Autowired
  public AwsCloudContextService(
      WorkspaceDao workspaceDao, FeatureService featureService, AwsConfiguration awsConfiguration) {
    this.awsConfiguration = awsConfiguration;
    this.workspaceDao = workspaceDao;
    this.featureService = featureService;
    initializeEnvironmentDiscovery();
  }

  /** Returns authentication from configuration */
  public Authentication getRequiredAuthentication() {
    if (awsConfiguration == null) {
      throw new InvalidApplicationConfigException("AWS configuration not initialized");
    }
    return awsConfiguration.getAuthentication();
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
   * Return a new AWS cloud context for discovered environment
   *
   * @return AWS cloud context
   */
  public AwsCloudContext getCloudContext() {
    return getCloudContext(discoverEnvironment());
  }

  /**
   * Return a new AWS cloud context for discovered environment
   *
   * @param environment AWS environment
   * @return AWS cloud context
   */
  public AwsCloudContext getCloudContext(Environment environment) {
    Metadata metadata = environment.getMetadata();
    return new AwsCloudContext(
        metadata.getMajorVersion(),
        metadata.getOrganizationId(),
        metadata.getAccountId(),
        metadata.getTenantAlias(),
        metadata.getEnvironmentAlias());
  }

  /**
   * Discover environment & return a verified environment
   *
   * @return AWS environment
   */
  public Environment discoverEnvironment() throws IllegalArgumentException, InternalLogicException {
    try {
      initializeEnvironmentDiscovery();

      if (this.environmentDiscovery == null) {
        throw new InvalidApplicationConfigException("AWS environmentDiscovery not initialized");
      }
      return environmentDiscovery.discoverEnvironment();
    } catch (IOException e) {
      throw new InternalLogicException("AWS discover environment error", e);
    }
  }

  /**
   * Return the landing zone to for the given cloud context's region
   *
   * @param awsCloudContext AWS cloud context
   * @param region AWS region
   * @return AWS landing zone, if supported for the Cloud context region
   * @throws StaleConfigurationException StaleConfigurationException
   */
  public Optional<LandingZone> getLandingZone(AwsCloudContext awsCloudContext, Region region) {
    Environment environment = discoverEnvironment();

    AwsCloudContext awsCloudContextFromEnv = getCloudContext(environment);
    if (!awsCloudContext.equals(awsCloudContextFromEnv)) {
      throw new StaleConfigurationException(
          String.format(
              "AWS cloud context expected %s, actual %s", awsCloudContext, awsCloudContextFromEnv));
    }

    return environment.getLandingZone(region);
  }

  private void initializeEnvironmentDiscovery() {
    this.environmentDiscovery =
        (environmentDiscovery == null && featureService.awsEnabled())
            ? AwsUtils.createEnvironmentDiscovery(awsConfiguration)
            : null;
  }
}
