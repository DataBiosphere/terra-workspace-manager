package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.CachedEnvironmentDiscovery;
import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook.ControlledAwsSagemakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.repackaged.org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tagging;
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

  private static final int MAX_RESULTS_PER_REQUEST_S3 = 1000;

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

  public static void appendUserTags(Collection<Tag> tags, SamUser user) {
    tags.add(Tag.builder().key("UserID").value(user.getSubjectId()).build());
  }

  public static void appendResourceTags(Collection<Tag> tags, AwsCloudContext awsCloudContext) {
    tags.add(Tag.builder().key("Version").value(awsCloudContext.getMajorVersion()).build());
    tags.add(Tag.builder().key("Tenant").value(awsCloudContext.getTenantAlias()).build());
    tags.add(Tag.builder().key("Environment").value(awsCloudContext.getEnvironmentAlias()).build());
  }

  public static <T extends ControlledResource> void appendPrincipalTags(
      Collection<Tag> tags, AwsCloudContext awsCloudContext, T awsResource) {
    tags.add(Tag.builder().key("Version").value(awsCloudContext.getMajorVersion()).build());

    if (awsResource instanceof ControlledAwsS3StorageFolderResource) {
      ControlledAwsS3StorageFolderResource resource =
          (ControlledAwsS3StorageFolderResource) awsResource;
      tags.add(Tag.builder().key("S3BucketID").value(resource.getBucketName()).build());
      tags.add(Tag.builder().key("TerraBucketID").value(resource.getPrefix()).build());

    } else if (awsResource instanceof ControlledAwsSagemakerNotebookResource) {
      ControlledAwsSagemakerNotebookResource resource =
          (ControlledAwsSagemakerNotebookResource) awsResource;
      // TODO(TERRA-550) Add sagemaker tags
    }
  }

  public static void appendRoleTags(Collection<Tag> tags, ApiAwsCredentialAccessScope accessScope) {
    tags.add(
        Tag.builder()
            .key("WorkspaceRole")
            .value((accessScope == ApiAwsCredentialAccessScope.WRITE_READ) ? "writer" : "reader")
            .build());
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

  // TODO(TERRA-498) Move all functions below this comment to CRL
  private static S3Client getS3Client(
      AwsCredentialsProvider awsCredentialsProvider, Region region) {
    return S3Client.builder().region(region).credentialsProvider(awsCredentialsProvider).build();
  }

  /**
   * Check if AWS storage object exists with given prefix as a folder
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param folder folder name (key)
   */
  public static boolean checkFolderExists(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String folder) {
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    return CollectionUtils.isNotEmpty(
        getObjectKeysByPrefix(awsCredentialsProvider, region, bucketName, folderKey, 1));
  }

  /**
   * Create AWS storage object (as a folder)
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param folder folder name (key)
   * @param tags collection of {@link Tag} to be attached to the folder
   */
  public static void createFolder(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String folder,
      Collection<Tag> tags) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    putObject(awsCredentialsProvider, region, bucketName, folderKey, "", tags);
  }

  /**
   * Delete AWS storage objects (as a folder) including all objects under it
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param folder folder name (key)
   */
  public static void deleteFolder(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String folder) {
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    List<String> objectKeys =
        getObjectKeysByPrefix(
            awsCredentialsProvider, region, bucketName, folderKey, Integer.MAX_VALUE);
    deleteObjects(awsCredentialsProvider, region, bucketName, objectKeys);
  }

  /**
   * Create AWS storage object
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param key object (key)
   * @param tags collection of {@link Tag} to be attached to the folder
   */
  public static void putObject(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String key,
      String content,
      Collection<Tag> tags) {
    S3Client s3Client = getS3Client(awsCredentialsProvider, region);

    logger.info(
        "Creating object with name '{}', key '{}' and {} content.",
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

    try {
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
      checkException(e);
      throw new ApiException("Error creating storage object", e);
    }
  }

  /**
   * Get a list of all objects (key) with given common prefix
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param prefix common prefix
   * @param limit max count of results
   */
  public static List<String> getObjectKeysByPrefix(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String prefix,
      int limit) {
    S3Client s3Client = getS3Client(awsCredentialsProvider, region);

    ListObjectsV2Request.Builder requestBuilder =
        ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).delimiter("/");

    int limitRemaining = limit;
    String continuationToken = null;
    List<String> objectKeys = new ArrayList<>();
    try {
      while (limitRemaining > 0) {
        int curLimit = Math.min(MAX_RESULTS_PER_REQUEST_S3, limitRemaining);
        limitRemaining -= curLimit;

        ListObjectsV2Response listResponse =
            s3Client.listObjectsV2(
                requestBuilder.continuationToken(continuationToken).maxKeys(curLimit).build());

        SdkHttpResponse httpResponse = listResponse.sdkHttpResponse();
        if (!httpResponse.isSuccessful()) {
          throw new ApiException(
              "Error listing storage objects, "
                  + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
        }

        objectKeys.addAll(listResponse.contents().stream().map(S3Object::key).toList());
        if (!listResponse.isTruncated()) {
          break; // ignore limitRemaining if there are no more results
        }
        continuationToken = listResponse.continuationToken();
      }
      return objectKeys;

    } catch (SdkException e) {
      checkException(e);
      throw new ApiException("Error listing storage objects", e);
    }
  }

  /**
   * Delete AWS storage objects by their keys
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param keys list of objects (keys)
   */
  public static void deleteObjects(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      List<String> keys) {

    S3Client s3Client = getS3Client(awsCredentialsProvider, region);

    DeleteObjectsRequest.Builder deleteRequestBuilder =
        DeleteObjectsRequest.builder().bucket(bucketName);

    try {
      ListUtils.partition(keys, MAX_RESULTS_PER_REQUEST_S3)
          .forEach(
              keysList -> {
                logger.info("Deleting storage objects with keys {}.", keysList);

                Collection<ObjectIdentifier> objectIds =
                    keysList.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList();

                DeleteObjectsResponse deleteResponse =
                    s3Client.deleteObjects(
                        deleteRequestBuilder
                            .delete(Delete.builder().objects(objectIds).quiet(true).build())
                            .build());

                SdkHttpResponse deleteHttpResponse = deleteResponse.sdkHttpResponse();
                if (!deleteHttpResponse.isSuccessful()) {
                  throw new ApiException(
                      "Error deleting storage objects: "
                          + deleteHttpResponse
                              .statusText()
                              .orElse(String.valueOf(deleteHttpResponse.statusCode())));
                }
                // Errors with individual objects are captured here (including 404)
                deleteResponse
                    .errors()
                    .forEach(err -> logger.warn("Failed to delete storage objects: {}", err));
              });
    } catch (SdkException e) {
      // Bulk delete operation would not fail with NotFound error, overall op is idempotent
      checkException(e);
      throw new ApiException("Error deleting storage objects", e);
    }
  }

  private static void checkException(SdkException ex)
      throws NotFoundException, UnauthorizedException, BadRequestException {
    String message = ex.getMessage();
    if (message.contains("ResourceNotFoundException") || message.contains("RecordNotFound")) {
      throw new NotFoundException("Resource deleted or no longer accessible", ex);

    } else if (message.contains("not authorized to perform")) {
      throw new UnauthorizedException(
          "Error performing resource operation, check the name / permissions and retry", ex);

    } else if (message.contains("Unable to transition to")) {
      throw new BadRequestException("Unable to perform resource lifecycle operation", ex);
    }
  }
}
