package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.service.features.FeatureService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Tag("aws-connected")
public class AwsUtilsTest extends BaseAwsConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtilsTest.class);
  @Autowired private AwsConfiguration awsConfiguration;
  @Autowired private FeatureService featureService;

  private void logEnvironmentMetadata(Metadata metadata) {
    logger.info("AWS Environment Infrastructure Details:");
    logger.info("       Terra Tenant Alias: {}", metadata.getTenantAlias());
    logger.info("      AWS Organization ID: {}", metadata.getOrganizationId());
    logger.info("  Terra Environment Alias: {}", metadata.getEnvironmentAlias());
    logger.info("           AWS Account ID: {}", metadata.getAccountId());
    logger.info("     Module Major Version: {}", metadata.getMajorVersion());
  }

  @Test
  void hello_bucket() throws IOException {
    Assertions.assertDoesNotThrow(() -> featureService.awsEnabledCheck());

    // Log the AWS config
    logger.info("AWS Configuration: {}", awsConfiguration.toString());

    // Create the EnvironmentDiscovery instance.  This should be created at service instantiation
    // and live for the lifetime of the service process.
    var environmentDiscovery = AwsUtils.createEnvironmentDiscovery(awsConfiguration);

    // Discover the environment.  This should be done at the scope of a single API operation;
    // caching will happen under the hood to prevent too many S3 calls to the discovery bucket.
    Environment environment = environmentDiscovery.discoverEnvironment();

    // This lifetime should track that of the discovered environment where possible (stale
    // credentials will get refreshed under the hood).
    AwsCredentialsProvider awsCredentialsProvider =
        AwsUtils.createWsmCredentialProvider(awsConfiguration.getAuthentication(), environment);

    // Log details about the discovered environment.
    logEnvironmentMetadata(environment.getMetadata());

    // Iterate over supported regions in Environment.

    for (Region region : environment.getSupportedRegions()) {
      Optional<LandingZone> landingZoneOptional = environment.getLandingZone(region);
      Assertions.assertTrue(landingZoneOptional.isPresent());
      LandingZone landingZone = landingZoneOptional.get();

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
        Assertions.fail(String.format("HeadBucket request failed: %s", exception), exception);
      }

      logger.info(
          "Confirmed access to bucket '{}' in AWS Region {}", bucketName, region.toString());
    }
  }
}
