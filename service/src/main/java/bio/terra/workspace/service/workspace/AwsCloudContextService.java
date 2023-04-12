package bio.terra.workspace.service.workspace;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.KmsKey;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.aws.resource.discovery.NotebookLifecycleConfiguration;
import bio.terra.aws.resource.discovery.StorageBucket;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.*;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;

/**
 * This service provides methods for managing AWS cloud context. These methods do not perform any
 * access control and operate directly against the {@link WorkspaceDao}
 */
@Component
public class AwsCloudContextService {
  private final WorkspaceDao workspaceDao;
  private final EnvironmentDiscovery environmentDiscovery;

  @Autowired
  public AwsCloudContextService(
      WorkspaceDao workspaceDao,
      FeatureConfiguration featureConfiguration,
      AwsConfiguration awsConfiguration) {
    this.workspaceDao = workspaceDao;
    this.environmentDiscovery =
        featureConfiguration.isAwsEnabled()
            ? AwsUtils.createEnvironmentDiscovery(awsConfiguration)
            : null;
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
  public @NotNull AwsCloudContext getCloudContext() {
    return getCloudContext(discoverEnvironment());
  }

  /**
   * Return a new AWS cloud context for discovered environment
   *
   * @param environment AWS environment
   * @return AWS cloud context
   */
  public @NotNull AwsCloudContext getCloudContext(@NotNull Environment environment) {
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
  public @NotNull Environment discoverEnvironment()
      throws IllegalArgumentException, InternalLogicException {
    try {
      Assert.notNull(this.environmentDiscovery, "environmentDiscovery not configured");

      Environment environment = environmentDiscovery.discoverEnvironment();
      validate(environment, "");

      return environment;

    } catch (IllegalArgumentException e) {
      throw new InvalidApplicationConfigException("AWS configuration error: " + e.getMessage());

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
  public Optional<LandingZone> getLandingZone(
      @NotNull AwsCloudContext awsCloudContext, @NotNull Region region) {
    Environment environment = discoverEnvironment();

    AwsCloudContext awsCloudContextFromEnv = getCloudContext(environment);
    if (!awsCloudContext.equals(awsCloudContextFromEnv)) {
      throw new StaleConfigurationException(
          String.format(
              "AWS cloud context expected %s, actual %s", awsCloudContext, awsCloudContextFromEnv));
    }

    return environment.getLandingZone(region);
  }

  // TODO-Dex Move these to library
  /**
   * Validates AWS environment
   *
   * @param environment AWS environment
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(Environment environment, String prefix) throws IllegalArgumentException {
    Assert.notNull(environment, prefix + "environment null");
    String envPrefix = prefix + "environment.";

    validate(environment.getMetadata(), envPrefix);
    Assert.notNull(
        environment.getWorkspaceManagerRoleArn(), envPrefix + "workspaceManagerRoleArn null");
    Assert.notNull(environment.getUserRoleArn(), envPrefix + "userRoleArn null");
    Assert.notNull(environment.getNotebookRoleArn(), envPrefix + "notebookRoleArn null");

    Assert.notEmpty(environment.getSupportedRegions(), envPrefix + "supportedRegions empty");
    environment
        .getSupportedRegions()
        .forEach(region -> validate(environment.getLandingZone(region).orElse(null), envPrefix));
  }

  /**
   * Validates AWS landing zone
   *
   * @param landingZone AWS landing zone
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(LandingZone landingZone, String prefix) throws IllegalArgumentException {
    Assert.notNull(landingZone, prefix + "landingZone null");
    String lzPrefix = prefix + "landingZone.";

    validate(landingZone.getMetadata(), lzPrefix);
    validate(landingZone.getStorageBucket(), lzPrefix);
    validate(landingZone.getKmsKey(), lzPrefix);

    Assert.notEmpty(
        landingZone.getNotebookLifecycleConfigurations(),
        lzPrefix + "notebookLifecycleConfigurations empty");
    landingZone
        .getNotebookLifecycleConfigurations()
        .forEach(lcConfig -> validate(lcConfig, lzPrefix));
  }

  /**
   * Validates AWS metadata
   *
   * @param metadata AWS metadata
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(Metadata metadata, String prefix) throws IllegalArgumentException {
    Assert.notNull(metadata, prefix + "metadata null");
    String mdPrefix = prefix + "metadata.";

    Assert.hasLength(metadata.getTenantAlias(), mdPrefix + "tenantAlias empty");
    Assert.hasLength(metadata.getOrganizationId(), mdPrefix + "organizationId empty");
    Assert.hasLength(metadata.getEnvironmentAlias(), mdPrefix + "environmentAlias empty");
    Assert.hasLength(metadata.getAccountId(), mdPrefix + "accountId empty");
    Assert.notNull(metadata.getRegion(), mdPrefix + "region null");
    Assert.hasLength(metadata.getMajorVersion(), mdPrefix + "majorVersion empty");
  }

  /**
   * Validates AWS storage bucket
   *
   * @param storageBucket AWS storage bucket
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(StorageBucket storageBucket, String prefix) throws IllegalArgumentException {
    Assert.notNull(storageBucket, prefix + "storageBucket null");
    String sbPrefix = prefix + "storageBucket.";

    Assert.notNull(storageBucket.arn(), sbPrefix + "arn null");
    Assert.hasLength(storageBucket.name(), sbPrefix + "name empty");
  }

  /**
   * Validates AWS kms key
   *
   * @param kmsKey AWS kms key
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(KmsKey kmsKey, String prefix) throws IllegalArgumentException {
    Assert.notNull(kmsKey, prefix + "kmsKey null");
    String kmsPrefix = prefix + "kmsKey.";

    Assert.notNull(kmsKey.arn(), kmsPrefix + "arn null");
    Assert.notNull(kmsKey.id(), kmsPrefix + "id null");
  }

  /**
   * Validates AWS notebook lifecycle configuration
   *
   * @param notebookLifecycleConfiguration AWS notebook lifecycle configuration
   * @param prefix Error message prefix
   * @throws IllegalArgumentException environment error
   */
  public void validate(NotebookLifecycleConfiguration notebookLifecycleConfiguration, String prefix)
      throws IllegalArgumentException {
    Assert.notNull(notebookLifecycleConfiguration, prefix + "notebookLifecycleConfiguration null");
    String lcConfigPrefix = prefix + "kmsKey.";

    Assert.notNull(notebookLifecycleConfiguration.arn(), lcConfigPrefix + "arn null");
    Assert.hasLength(notebookLifecycleConfiguration.name(), lcConfigPrefix + "name empty");
  }
}
