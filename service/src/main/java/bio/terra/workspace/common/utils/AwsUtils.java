package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.CachedEnvironmentDiscovery;
import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.Tag;

public class AwsUtils {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtils.class);

  private static final int MAX_ROLE_SESSION_NAME_LENGTH = 64;

  /**
   * Truncate a passed string for use as an STS session name
   *
   * @param value String to truncate
   * @return truncated String
   */
  private static String getRoleSessionName(String value) {
    return (value.length() > MAX_ROLE_SESSION_NAME_LENGTH)
        ? value.substring(0, MAX_ROLE_SESSION_NAME_LENGTH - 1)
        : value;
  }

  /**
   * Helper function to build {@link AssumeRoleWithWebIdentityRequest} objects for {@link
   * AwsCredentialsProvider} refresh operations, adding relevant logging.
   */
  @Traced
  private static AssumeRoleWithWebIdentityRequest createRefreshRequest(
      Arn roleArn, Duration duration, String jwtAudience) {
    String idToken = GcpUtils.getWsmSaJwt(jwtAudience);

    logger.info(
        "Google JWT Claims: {}",
        JwtDecoders.fromOidcIssuerLocation("https://accounts.google.com")
            .decode(idToken)
            .getClaims()
            .toString());

    return AssumeRoleWithWebIdentityRequest.builder()
        .roleArn(roleArn.toString())
        .durationSeconds((int) duration.toSeconds())
        .roleSessionName(getRoleSessionName(GcpUtils.getWsmSaEmail()))
        .webIdentityToken(idToken)
        .build();
  }

  /**
   * Returns an {@link AwsCredentialsProvider} instance that assumes an IAM role using Web Identity
   * Federation, passing a Google Identity credential representing the running WSM GCP Service
   * Account.
   *
   * <p>This instance will automatically cache credentials for this assumed role until they are
   * within {@param staleTime} of expiring, and then automatically refresh the credentials with a
   * new STS Web Identity Federation call, made with a freshly obtained GCP SA JWT.
   *
   * @param roleArn AWS IAM role to assume, must have a trust policy allowing WSM GCP SA
   * @param duration session duration (see {@link
   *     AssumeRoleWithWebIdentityRequest.Builder#durationSeconds})
   * @param staleTime time, relative to token expiration, that a cached token should be considered
   *     stale and refreshed
   * @param jwtAudience target audience to pass when calling {@link GcpUtils#getWsmSaJwt} at token
   *     refresh time
   * @return AwsCredentialsProvider
   */
  private static AwsCredentialsProvider createAssumeRoleWithGcpCredentialsProvider(
      Arn roleArn, Duration duration, Duration staleTime, String jwtAudience) {
    return StsAssumeRoleWithWebIdentityCredentialsProvider.builder()
        .stsClient(
            StsClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.AWS_GLOBAL)
                .build())
        .staleTime(staleTime)
        .refreshRequest(() -> createRefreshRequest(roleArn, duration, jwtAudience))
        .build();
  }

  /**
   * Helper function to be called by {@link #createEnvironmentDiscovery} to get a long-lived
   * credential for resource discovery purposes.
   *
   * <p>Calls {@link #createAssumeRoleWithGcpCredentialsProvider} to get a long-lived {@link
   * AwsCredentialsProvider} instance to authenticate as the TerraDiscovery IAM Role.
   */
  private static AwsCredentialsProvider createDiscoveryCredentialsProvider(
      AwsConfiguration awsConfiguration) {
    AwsConfiguration.Authentication authenticationConfig = awsConfiguration.getAuthentication();
    return createAssumeRoleWithGcpCredentialsProvider(
        Arn.fromString(awsConfiguration.getDiscovery().getRoleArn()),
        Duration.ofSeconds(authenticationConfig.getCredentialLifetimeSeconds()),
        Duration.ofSeconds(authenticationConfig.getCredentialStaleTimeSeconds()),
        authenticationConfig.getGoogleJwtAudience());
  }

  /**
   * Creates a long-lived {@link EnvironmentDiscovery} instance to use through the process lifetime
   * of the Workspace Manager service.
   *
   * <p>This depends on the following configuration options being configured, as consumed from the
   * passed {@link AwsConfiguration} object:
   *
   * <ul>
   *   <li>workspace.aws.discovery.roleArn - must be set to AWS IAM Role ARN with access to
   *       discovery bucket
   *   <li>workspace.aws.discovery.bucket.name - must be set the name of the discovery bucket
   *   <li>workspace.aws.discovery.bucket.region - must be set to the region of the discovery bucket
   *   <li>workspace.aws.authentication.googleJwtAudience - must be set to the audience claim
   *       expected by the AWS trust policy that allows the Google SA to assumes the AWS IAM Role
   * </ul>
   *
   * @param awsConfiguration Spring configuration object containing required parameters (as
   *     described above)
   * @return EnvironmentDiscovery
   */
  public static EnvironmentDiscovery createEnvironmentDiscovery(AwsConfiguration awsConfiguration) {
    AwsConfiguration.Discovery discoveryConfig = awsConfiguration.getDiscovery();
    AwsConfiguration.Discovery.Bucket discoveryBucketConfig = discoveryConfig.getBucket();
    AwsConfiguration.Discovery.Caching discoveryCachingConfig = discoveryConfig.getCaching();

    // Create an EnvironmentDiscovery instance that pulls discovery data from an S3 bucket, assuming
    // the configured discovery IAM Role using Google SA creds.
    S3EnvironmentDiscovery s3EnvironmentDiscovery =
        new S3EnvironmentDiscovery(
            discoveryBucketConfig.getName(),
            S3Client.builder()
                .region(Region.of(discoveryBucketConfig.getRegion()))
                .credentialsProvider(createDiscoveryCredentialsProvider(awsConfiguration))
                .build());

    // If caching is enabled for discovery, create a CachedEnvironmentDiscovery using the
    // S3EnvironmentDiscovery instance.  This will prevent unnecessary bucket reads, as we expect
    // changes to the discovery bucket to be infrequent.
    if (discoveryCachingConfig.isEnabled()) {
      return new CachedEnvironmentDiscovery(
          s3EnvironmentDiscovery,
          Duration.ofSeconds(discoveryConfig.getCaching().getExpirationTimeSeconds()));
    }

    return s3EnvironmentDiscovery;
  }

  /**
   * Obtain an {@link AwsCredentialsProvider} instance which will used to provide credentials
   * assuming the Terra Workspace Manager AWS IAM Role.
   *
   * <p>This object should have a short-to-medium lifetime. Its lifetime should align to the
   * lifetime of the passed {@link Environment} object, and these lifetimes should roughly
   * correspond to a single API call or Stairway flight.
   *
   * @param authentication an {@link AwsConfiguration.Authentication} AwS authentication config
   * @param environment a discovered {@link Environment} object corresponding to the AWS Environment
   *     to obtain Terra Workspace Manager IAM Role credentials for
   * @return AwsCredentialsProvider
   */
  public static AwsCredentialsProvider createWsmCredentialProvider(
      AwsConfiguration.Authentication authentication, Environment environment) {
    return createAssumeRoleWithGcpCredentialsProvider(
        environment.getWorkspaceManagerRoleArn(),
        Duration.ofSeconds(authentication.getCredentialLifetimeSeconds()),
        Duration.ofSeconds(authentication.getCredentialStaleTimeSeconds()),
        authentication.getGoogleJwtAudience());
  }

  public static void appendResourceTags(
      @NotNull Collection<Tag> tags, SamUser samUser, UUID workspaceUuid) {
    if (samUser != null) {
      tags.add(Tag.builder().key("user_email").value(samUser.getEmail()).build());
      tags.add(Tag.builder().key("user_id").value(samUser.getSubjectId()).build());
    }

    if (workspaceUuid != null) {
      tags.add(Tag.builder().key("ws_id").value(workspaceUuid.toString()).build());
    }
  }

  private static S3Client getS3Client(
      AwsCredentialsProvider awsCredentialsProvider, Region region) {
    return S3Client.builder().region(region).credentialsProvider(awsCredentialsProvider).build();
  }

  public static void createS3Folder(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String folder,
      Collection<Tag> tags) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    putS3Object(awsCredentialsProvider, region, bucketName, folderKey, "", tags);
  }

  public static void putS3Object(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String key,
      String content,
      Collection<Tag> tags) {
    try {
      S3Client s3Client = getS3Client(awsCredentialsProvider, region);

      logger.info(
          "Creating S3 object with name '{}', key '{}' and {} content.",
          bucketName,
          key,
          StringUtils.isEmpty(content) ? "(empty)" : "");

      Set<software.amazon.awssdk.services.s3.model.Tag> s3Tags =
          tags.stream()
              .map(
                  stsTag ->
                      software.amazon.awssdk.services.s3.model.Tag.builder()
                          .key(stsTag.key())
                          .value(stsTag.value())
                          .build())
              .collect(Collectors.toSet());

      PutObjectRequest.Builder requestBuilder =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .tagging(Tagging.builder().tagSet(s3Tags).build());

      SdkHttpResponse httpResponse =
          s3Client
              .putObject(requestBuilder.build(), RequestBody.fromString(content))
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error creating storage object, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      // checkException(e); TODO-Dex
      throw new ApiException("Error creating storage object", e);
    }
  }
}
