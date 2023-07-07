package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.InvalidCloudContextStateException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Tag("aws-connected")
@TestInstance(Lifecycle.PER_CLASS)
public class AwsCloudContextConnectedTest extends BaseAwsConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(AwsCloudContextConnectedTest.class);

  private void logMetadata(Metadata metadata, String heading) {
    logger.info(heading);
    logger.info("       Terra Tenant Alias: {}", metadata.getTenantAlias());
    logger.info("      AWS Organization ID: {}", metadata.getOrganizationId());
    logger.info("  Terra Environment Alias: {}", metadata.getEnvironmentAlias());
    logger.info("           AWS Account ID: {}", metadata.getAccountId());
    logger.info("               AWS Region: {}", metadata.getRegion());
    logger.info("     Module Major Version: {}", metadata.getMajorVersion());
  }

  @Test
  void discoverEnvironmentTest() throws IOException {
    Assertions.assertDoesNotThrow(() -> featureService.awsEnabledCheck());

    // Log the AWS config
    logger.info("AWS Configuration: {}", awsConfiguration.toString());

    // Create the EnvironmentDiscovery instance.  This should be created at service instantiation
    // and live for the lifetime of the service process.
    EnvironmentDiscovery environmentDiscovery =
        AwsUtils.createEnvironmentDiscovery(awsConfiguration);

    // Discover the environment.  This should be done at the scope of a single API operation;
    // caching will happen under the hood to prevent too many S3 calls to the discovery bucket.
    Environment environment = environmentDiscovery.discoverEnvironment();
    assertNotNull(environment.getMetadata(), "environment.metadata null");
    assertNotNull(
        environment.getWorkspaceManagerRoleArn(), "environment.workspaceManagerRoleArn null");
    assertNotNull(environment.getUserRoleArn(), "environment.userRoleArn null");
    assertNotNull(environment.getNotebookRoleArn(), "environment.notebookRoleArn null");
    assertFalse(environment.getSupportedRegions().isEmpty(), "environment.supportedRegions empty");

    // This lifetime should track that of the discovered environment where possible (stale
    // credentials will get refreshed under the hood).
    AwsCredentialsProvider awsCredentialsProvider =
        AwsUtils.createWsmCredentialProvider(awsConfiguration.getAuthentication(), environment);

    // Log details about the discovered environment.
    logMetadata(environment.getMetadata(), "AWS Environment Infrastructure Details:");

    // Iterate over supported regions in Environment.
    for (Region region : environment.getSupportedRegions()) {
      Optional<LandingZone> landingZoneOptional = environment.getLandingZone(region);
      assertTrue(landingZoneOptional.isPresent());

      LandingZone landingZone = landingZoneOptional.get();
      assertNotNull(landingZone.getMetadata(), region + ": landingZone.metadata null");
      assertNotNull(landingZone.getStorageBucket(), region + ": landingZone.storageBucket null");
      assertNotNull(landingZone.getKmsKey(), region + ": landingZone.kmsKey null");
      assertFalse(
          landingZone.getNotebookLifecycleConfigurations().isEmpty(),
          region + ": landingZone.notebookLifecycleConfigurations empty");

      // Log details about the landing zone.
      logMetadata(landingZone.getMetadata(), "Landing zone region: " + region);

      // Perform a HeadBucket command to check storage bucket existence; this requires ListBucket
      // permissions on the bucket, which the WSM role has.  This is a basic "Hello, world!" test
      // that we've discovered the environment and assumed the TerraWorkspaceManager role.

      String bucketName = landingZone.getStorageBucket().name();
      HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucketName).build();

      // SdkClient classes should generally be used in try-with-resource blocks.
      try (S3Client s3Client =
          S3Client.builder().region(region).credentialsProvider(awsCredentialsProvider).build()) {
        s3Client.headBucket(request);
      } catch (Exception exception) {
        fail(String.format("HeadBucket request failed: %s", exception), exception);
      }
    }
  }

  @Test
  void createCloudContextTest() {
    AwsCloudContext createdCloudContext =
        awsCloudContextService.createCloudContext(
            "flightId", WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID);
    assertNotNull(createdCloudContext);

    Metadata envMetadata = awsCloudContextService.discoverEnvironment().getMetadata();
    AwsCloudContextFields contextFields = createdCloudContext.getContextFields();
    assertNotNull(contextFields);
    assertEquals(envMetadata.getMajorVersion(), contextFields.getMajorVersion());
    assertEquals(envMetadata.getOrganizationId(), contextFields.getOrganizationId());
    assertEquals(envMetadata.getAccountId(), contextFields.getAccountId());
    assertEquals(envMetadata.getTenantAlias(), contextFields.getTenantAlias());
    assertEquals(envMetadata.getEnvironmentAlias(), contextFields.getEnvironmentAlias());

    CloudContextCommonFields commonFields = createdCloudContext.getCommonFields();
    assertNotNull(commonFields);
    assertEquals("flightId", commonFields.flightId());
    assertEquals(WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID, commonFields.spendProfileId());
    assertEquals(WsmResourceState.CREATING, commonFields.state());
    assertNull(commonFields.error());
  }

  @Test
  void getLandingZoneTest() throws IOException {
    AwsCloudContext cloudContext = awsConnectedTestUtils.getAwsCloudContext();

    // success
    Optional<LandingZone> landingZone =
        awsCloudContextService.getLandingZone(
            cloudContext,
            awsCloudContextService.discoverEnvironment().getSupportedRegions().iterator().next());
    assertTrue(landingZone.isPresent(), "landing zone expected for valid region");

    // failure - no landing zone for region
    landingZone = awsCloudContextService.getLandingZone(cloudContext, Region.of("cloud"));
    assertFalse(landingZone.isPresent(), "landing zone not expected for invalid region");

    CloudContextCommonFields commonFields =
        new CloudContextCommonFields(
            WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID, WsmResourceState.READY, "i", null);

    // error - bad cloud context
    assertThrows(
        InvalidCloudContextStateException.class,
        () ->
            awsCloudContextService.getLandingZone(
                new AwsCloudContext(null, commonFields), Region.of("cloud")));

    // error - cloud context mismatch
    assertThrows(
        StaleConfigurationException.class,
        () ->
            awsCloudContextService.getLandingZone(
                new AwsCloudContext(
                    new AwsCloudContextFields("a", "b", "c", "d", "e"), commonFields),
                Region.of("cloud")));
  }
}
