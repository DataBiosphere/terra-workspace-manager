package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.CachedEnvironmentDiscovery;
import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder.ControlledAwsStorageFolderResource;
import bio.terra.workspace.service.resource.controlled.exception.AwsGenericServiceException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.Duration;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.Tag;

public class AwsUtils {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtils.class);

  private static final int MAX_ROLE_SESSION_NAME_LENGTH = 64;
  private static final Duration MIN_ROLE_SESSION_TOKEN_DURATION = Duration.ofSeconds(900);

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

  public static void appendUserTags(Collection<Tag> tags, AuthenticatedUserRequest userRequest) {
    tags.add(Tag.builder().key("UserID").value(userRequest.getSubjectId()).build());
  }

  public static void appendResourceTags(Collection<Tag> tags, AwsCloudContext awsCloudContext) {
    tags.add(Tag.builder().key("Version").value(awsCloudContext.getMajorVersion()).build());
    tags.add(Tag.builder().key("Tenant").value(awsCloudContext.getTenantAlias()).build());
    tags.add(Tag.builder().key("Environment").value(awsCloudContext.getEnvironmentAlias()).build());
  }

  public static void appendPrincipalTags(
      Collection<Tag> tags,
      AwsCloudContext awsCloudContext,
      ControlledAwsStorageFolderResource awsStorageFolderResource) {
    tags.add(Tag.builder().key("Version").value(awsCloudContext.getMajorVersion()).build());
    tags.add(
        Tag.builder().key("S3BucketID").value(awsStorageFolderResource.getBucketName()).build());
    tags.add(
        Tag.builder().key("TerraBucketID").value(awsStorageFolderResource.getPrefix()).build());
  }

  public static void appendRoleTags(Collection<Tag> tags, ApiAwsCredentialAccessScope accessScope) {
    tags.add(
        Tag.builder()
            .key("WorkspaceRole")
            .value((accessScope == ApiAwsCredentialAccessScope.WRITE_READ) ? "writer" : "reader")
            .build());
  }

  /**
   * Convert a collection of (STS library) Tag objects to S3 library Tag objects. Each AWS library
   * defines its own class for a Tag, though they each represent a String key-value pair.
   */
  public static Collection<software.amazon.awssdk.services.s3.model.Tag> convertTags(
      Collection<Tag> tags) {
    return tags.stream()
        .map(
            t ->
                software.amazon.awssdk.services.s3.model.Tag.builder()
                    .key(t.key())
                    .value(t.value())
                    .build())
        .toList();
  }

  private static StsClient getStsClient() {
    return getStsClient(AnonymousCredentialsProvider.create());
  }

  private static StsClient getStsClient(AwsCredentialsProvider credentialsProvider) {
    return StsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.AWS_GLOBAL)
        .build();
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
  public static AwsCredentialsProvider createAssumeRoleWithGcpCredentialsProvider(
      Arn roleArn, Duration duration, Duration staleTime, String jwtAudience) {
    return StsAssumeRoleWithWebIdentityCredentialsProvider.builder()
        .stsClient(getStsClient())
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
   *
   * @param awsConfiguration an {@link AwsConfiguration}
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

  public static Credentials getAssumeServiceRoleCredentials(
      AwsConfiguration.Authentication authentication, Environment environment, Duration duration) {
    AssumeRoleWithWebIdentityRequest request =
        createRefreshRequest(
            environment.getWorkspaceManagerRoleArn(),
            duration,
            authentication.getGoogleJwtAudience());
    logger.info(
        "Assuming Service role ('{}') with session name `{}`, duration {} seconds.",
        request.roleArn(),
        request.roleSessionName(),
        request.durationSeconds());

    return getStsClient().assumeRoleWithWebIdentity(request).credentials();
  }

  public static Credentials getAssumeUserRoleCredentials(
      AwsConfiguration.Authentication authentication,
      Environment environment,
      SamUser user,
      Duration duration,
      Collection<Tag> tags) {
    Credentials serviceCredentials =
        getAssumeServiceRoleCredentials(
            authentication, environment, MIN_ROLE_SESSION_TOKEN_DURATION);
    return assumeUserRoleFromServiceCredentials(
        environment, serviceCredentials, user, duration, tags);
  }

  /**
   * Wraps an AwsServiceException in an ErrorReportException-based class and determines whether a
   * retry is necessary.
   *
   * @param message A message to prepend to the exception's message
   * @param ex An AwsServiceException thrown by an AWS client
   * @return A StepResult with status STEP_RESULT_FAILURE_RETRY or _FATAL, depending on ex.
   */
  public static StepResult handleAwsExceptionInFlight(String message, AwsServiceException ex) {
    if (ex.retryable()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_RETRY, new AwsGenericServiceException(message, ex));
    } else {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new AwsGenericServiceException(message, ex));
    }
  }

  public static Credentials assumeUserRoleFromServiceCredentials(
      Environment environment,
      Credentials serviceCredentials,
      SamUser user,
      Duration duration,
      Collection<Tag> tags) {
    AssumeRoleRequest request =
        AssumeRoleRequest.builder()
            .durationSeconds((int) duration.toSeconds())
            .roleArn(environment.getUserRoleArn().toString())
            .roleSessionName(getRoleSessionName(user.getEmail()))
            .tags(tags)
            .build();

    logger.info(
        "Assuming User role ('{}') with session name `{}`, duration {} seconds, and tags: '{}'.",
        request.roleArn(),
        request.roleSessionName(),
        request.durationSeconds(),
        request.tags());

    AwsSessionCredentials sessionCredentials =
        AwsSessionCredentials.create(
            serviceCredentials.accessKeyId(),
            serviceCredentials.secretAccessKey(),
            serviceCredentials.sessionToken());

    return getStsClient(StaticCredentialsProvider.create(sessionCredentials))
        .assumeRole(request)
        .credentials();
  }
}
