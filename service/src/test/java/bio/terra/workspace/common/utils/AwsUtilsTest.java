package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Tag("awsConnected")
public class AwsUtilsTest extends BaseAwsConnectedTest {

  @Autowired private AwsConfiguration awsConfiguration;
  @Autowired private FeatureConfiguration featureConfiguration;

  @Test
  void hello_bucket() throws IOException {
    Assertions.assertDoesNotThrow(
        () -> {
          featureConfiguration.awsEnabledCheck();
        });

    // Create the EnvironmentDiscovery instance.  This should be created at service instantiation
    // and live for the lifetime of the service process.
    var environmentDiscovery = AwsUtils.createEnvironmentDiscovery(awsConfiguration);

    // Discover the environment.  This should be done at the scope of a single API operation;
    // caching will happen under the hood to prevent too many S3 calls to the discovery bucket.
    Environment environment = environmentDiscovery.discoverEnvironment();

    // This lifetime should track that of the discovered environment where possible (stale creds
    // will get refreshed under the hood).
    AwsCredentialsProvider awsCredentialsProvider =
        AwsUtils.createWsmCredentialProvider(awsConfiguration, environment);

    // Iterate over supported regions in Environment.

    for (Region region : environmentDiscovery.discoverEnvironment().getSupportedRegions()) {
      Optional<LandingZone> landingZoneOptional = environment.getLandingZone(region);
      Assertions.assertTrue(landingZoneOptional.isPresent());
      LandingZone landingZone = landingZoneOptional.get();

      // Perform a HeadBucket command to check storage bucket existence; this requires ListBucket
      // permissions on the bucket, which the WSM role has.  This is a basic "Hello, world!" test
      // that we've discovered the environment and assumed the TerraWorkspaceManager role.

      S3Client s3Client =
          S3Client.builder().region(region).credentialsProvider(awsCredentialsProvider).build();

      HeadBucketRequest request =
          HeadBucketRequest.builder().bucket(landingZone.getStorageBucket().name()).build();

      Assertions.assertDoesNotThrow(
          () -> {
            s3Client.headBucket(request);
          });
    }
  }
}
