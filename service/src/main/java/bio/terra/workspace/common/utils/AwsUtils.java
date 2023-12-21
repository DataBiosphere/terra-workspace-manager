package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.CachedEnvironmentDiscovery;
import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.cloudres.aws.ec2.EC2SecurityGroupCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
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
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.model.StartNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.StopNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;
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
  public static final String TAG_KEY_USER_ID = "UserID";
  public static final String TAG_KEY_VERSION = "Version";
  public static final String TAG_KEY_TENANT = "Tenant";
  public static final String TAG_KEY_ENVIRONMENT = "Environment";
  public static final String TAG_KEY_WORKSPACE_ID = "WorkspaceId";
  public static final String TAG_KEY_S3_BUCKET_ID = "S3BucketID";
  public static final String TAG_KEY_TERRA_BUCKET_ID = "TerraBucketID";
  public static final String TAG_KEY_WORKSPACE_ROLE = "WorkspaceRole";

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

  private static void addOrUpdateTag(Collection<Tag> tags, String key, String value) {
    // cannot in-place update, hence remove and re-add
    Collection<Tag> removeTags =
        tags.stream().filter(t -> key.equals(t.key())).collect(Collectors.toSet());
    tags.removeAll(removeTags);
    tags.add(Tag.builder().key(key).value(value).build());
  }

  public static void appendUserTags(Collection<Tag> tags, SamUser user) {
    if (user != null) {
      addOrUpdateTag(tags, TAG_KEY_USER_ID, user.getSubjectId());
    }
  }

  public static <T extends ControlledResource> void appendResourceTags(
      Collection<Tag> tags, AwsCloudContext awsCloudContext, @Nullable T awsResource) {
    addOrUpdateTag(tags, TAG_KEY_VERSION, awsCloudContext.getMajorVersion());
    addOrUpdateTag(tags, TAG_KEY_TENANT, awsCloudContext.getTenantAlias());
    addOrUpdateTag(tags, TAG_KEY_ENVIRONMENT, awsCloudContext.getEnvironmentAlias());

    if (awsResource != null) {
      addOrUpdateTag(tags, TAG_KEY_WORKSPACE_ID, awsResource.getWorkspaceId().toString());
    }
  }

  public static <T extends ControlledResource> void appendPrincipalTags(
      Collection<Tag> tags, AwsCloudContext awsCloudContext, T awsResource) {
    addOrUpdateTag(tags, TAG_KEY_VERSION, awsCloudContext.getMajorVersion());

    if (awsResource instanceof ControlledAwsS3StorageFolderResource resource) {
      addOrUpdateTag(tags, TAG_KEY_S3_BUCKET_ID, resource.getBucketName());
      addOrUpdateTag(tags, TAG_KEY_TERRA_BUCKET_ID, resource.getPrefix());
    } else if (awsResource instanceof ControlledAwsSageMakerNotebookResource resource) {
      // TODO(TERRA-550) Add sageMaker tags
    }
  }

  public static void appendRoleTags(Collection<Tag> tags, ApiAwsCredentialAccessScope accessScope) {
    addOrUpdateTag(
        tags,
        TAG_KEY_WORKSPACE_ROLE,
        (accessScope == ApiAwsCredentialAccessScope.WRITE_READ) ? "writer" : "reader");
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
  @WithSpan
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
   * @return {@link AwsCredentialsProvider}
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
   * @param awsConfiguration {@link AwsConfiguration}
   */
  private static AwsCredentialsProvider createDiscoveryCredentialsProvider(
      AwsConfiguration awsConfiguration) {
    AwsConfiguration.Authentication authentication = awsConfiguration.getAuthentication();
    return createAssumeRoleWithGcpCredentialsProvider(
        Arn.fromString(awsConfiguration.getDiscovery().getRoleArn()),
        Duration.ofSeconds(authentication.getCredentialLifetimeSeconds()),
        Duration.ofSeconds(authentication.getCredentialStaleTimeSeconds()),
        authentication.getGoogleJwtAudience());
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
   * @return {@link EnvironmentDiscovery}
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
   * @return {@link AwsCredentialsProvider}
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
        "Assuming Service role ({}) with session name {}, duration {} seconds.",
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
        "Assuming User role ({}) with session name {}, duration {} seconds, and tags: {}.",
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

  public static S3Client getS3Client(AwsCredentialsProvider awsCredentialsProvider, Region region) {
    return S3Client.builder().region(region).credentialsProvider(awsCredentialsProvider).build();
  }

  public static SageMakerClient getSageMakerClient(
      AwsCredentialsProvider awsCredentialsProvider, Region region) {
    return SageMakerClient.builder()
        .region(region)
        .credentialsProvider(awsCredentialsProvider)
        .build();
  }

  public static SageMakerWaiter getSageMakerWaiter(SageMakerClient sageMakerClient) {
    return SageMakerWaiter.builder()
        .client(sageMakerClient)
        .overrideConfiguration(
            WaiterOverrideConfiguration.builder()
                .waitTimeout(AwsResourceConstants.SAGEMAKER_WAITER_TIMEOUT)
                .build())
        .build();
  }

  // AWS S3 Storage Folder

  // TODO(TERRA-498) Move storage functions below to CRL

  /**
   * Check if a AWS storage object exists with given prefix as a folder
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param storageResource {@link ControlledAwsS3StorageFolderResource}
   * @return True if the folder exists
   * @throws ApiException ApiException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static boolean checkFolderExists(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsS3StorageFolderResource storageResource) {
    String prefix = storageResource.getPrefix();
    String folderKey = prefix.endsWith("/") ? prefix : String.format("%s/", prefix);
    return CollectionUtils.isNotEmpty(
        getS3ObjectKeysByPrefix(
            awsCredentialsProvider,
            Region.of(storageResource.getRegion()),
            storageResource.getBucketName(),
            folderKey,
            1));
  }

  /**
   * Create AWS storage object (as a folder)
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param storageResource {@link ControlledAwsS3StorageFolderResource}
   * @param tags collection of {@link Tag} to be attached to the folder
   * @throws ApiException ApiException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static void createStorageFolder(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsS3StorageFolderResource storageResource,
      Collection<Tag> tags) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    String prefix = storageResource.getPrefix();
    String folderKey = prefix.endsWith("/") ? prefix : String.format("%s/", prefix);
    putS3Object(
        awsCredentialsProvider,
        Region.of(storageResource.getRegion()),
        storageResource.getBucketName(),
        folderKey,
        "",
        tags);
  }

  /**
   * Delete AWS storage objects (as a folder) including all objects under it
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param storageResource {@link ControlledAwsS3StorageFolderResource}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static void deleteStorageFolder(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsS3StorageFolderResource storageResource) {
    Region region = Region.of(storageResource.getRegion());
    String bucketName = storageResource.getBucketName();
    String prefix = storageResource.getPrefix();

    String folderKey = prefix.endsWith("/") ? prefix : String.format("%s/", prefix);
    List<String> objectKeys =
        getS3ObjectKeysByPrefix(
            awsCredentialsProvider, region, bucketName, folderKey, Integer.MAX_VALUE);

    if (CollectionUtils.isNotEmpty(objectKeys)) {
      deleteS3Objects(awsCredentialsProvider, region, bucketName, objectKeys);
    }
  }

  /**
   * Create AWS storage object
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param key object (key)
   * @param tags collection of {@link Tag} to be attached to the folder
   * @throws ApiException ApiException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static void putS3Object(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String key,
      String content,
      Collection<Tag> tags) {
    S3Client s3Client = getS3Client(awsCredentialsProvider, region);

    Set<software.amazon.awssdk.services.s3.model.Tag> s3Tags =
        tags.stream()
            .map(
                stsTag ->
                    software.amazon.awssdk.services.s3.model.Tag.builder()
                        .key(stsTag.key())
                        .value(stsTag.value())
                        .build())
            .collect(Collectors.toSet());

    logger.info(
        "Creating object with name {}, key {} and {} content.",
        bucketName,
        key,
        StringUtils.isEmpty(content) ? "(empty)" : "");

    try {
      SdkHttpResponse httpResponse =
          s3Client
              .putObject(
                  PutObjectRequest.builder()
                      .bucket(bucketName)
                      .key(key)
                      .tagging(Tagging.builder().tagSet(s3Tags).build())
                      .build(),
                  RequestBody.fromString(content))
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error creating storage object, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e, "Error creating storage object");
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
   * @throws ApiException ApiException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static List<String> getS3ObjectKeysByPrefix(
      AwsCredentialsProvider awsCredentialsProvider,
      Region region,
      String bucketName,
      String prefix,
      int limit) {
    S3Client s3Client = getS3Client(awsCredentialsProvider, region);

    String folderKey = prefix.endsWith("/") ? prefix : String.format("%s/", prefix);
    ListObjectsV2Request.Builder requestBuilder =
        ListObjectsV2Request.builder().bucket(bucketName).prefix(folderKey);

    int limitRemaining = limit <= 0 ? Integer.MAX_VALUE : limit;
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

    } catch (SdkException e) {
      checkException(e, "Error listing storage objects");
    }

    return objectKeys;
  }

  /**
   * Delete AWS storage objects by their keys
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param region {@link Region}
   * @param bucketName bucket name
   * @param keys list of objects (keys)
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static void deleteS3Objects(
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
                    .forEach(err -> logger.error("Failed to delete storage objects: {}", err));
              });

    } catch (SdkException e) {
      // Bulk delete operation would not fail with NotFound error, overall op is idempotent
      checkException(e, "Error deleting storage objects");
    }
  }

  // AWS SageMaker Notebook

  // TODO(TERRA-500) Move notebook functions below to CRL

  /**
   * Create a AWS SageMaker Notebook
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @param userRoleArn User role {@link Arn}
   * @param kmsKeyArn {@link Arn} for the KmsKey in the landing zone (region)
   * @param notebookLifecycleConfigArn {@link Arn} for the notebookLifecycleConfig in the landing
   *     zone (region)
   * @param tags collection of {@link Tag} to be attached to the folder
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void createSageMakerNotebook(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource,
      Arn userRoleArn,
      Arn kmsKeyArn,
      Arn notebookLifecycleConfigArn,
      Collection<Tag> tags) {
    SageMakerClient sageMakerClient =
        getSageMakerClient(awsCredentialsProvider, Region.of(notebookResource.getRegion()));

    Set<software.amazon.awssdk.services.sagemaker.model.Tag> sageMakerTags =
        tags.stream()
            .map(
                stsTag ->
                    software.amazon.awssdk.services.sagemaker.model.Tag.builder()
                        .key(stsTag.key())
                        .value(stsTag.value())
                        .build())
            .collect(Collectors.toSet());

    String policyName = null;
    if (notebookLifecycleConfigArn != null) {
      policyName = notebookLifecycleConfigArn.resource().resource();
    }

    logger.info(
        "Creating notebook with name {}, type {}, lifecycle policy {}.",
        notebookResource.getInstanceName(),
        notebookResource.getInstanceType(),
        policyName);

    try {
      SdkHttpResponse httpResponse =
          sageMakerClient
              .createNotebookInstance(
                  CreateNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookResource.getInstanceName())
                      .instanceType(notebookResource.getInstanceType())
                      .roleArn(userRoleArn.toString())
                      .kmsKeyId(kmsKeyArn.resource().resource())
                      .tags(sageMakerTags)
                      .lifecycleConfigName(policyName)
                      .build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error creating AWS SageMaker Notebook, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e, "Error creating AWS SageMaker Notebook");
    }
  }

  /**
   * Get a AWS SageMaker Notebook status
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @return {@link NotebookInstanceStatus}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   */
  public static NotebookInstanceStatus getSageMakerNotebookStatus(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource) {
    SageMakerClient sageMakerClient =
        getSageMakerClient(awsCredentialsProvider, Region.of(notebookResource.getRegion()));
    String notebookName = notebookResource.getInstanceName();

    try {
      DescribeNotebookInstanceResponse describeResponse =
          sageMakerClient.describeNotebookInstance(
              DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build());

      SdkHttpResponse httpResponse = describeResponse.sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error getting notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

      return describeResponse.notebookInstanceStatus();

    } catch (SdkException e) {
      checkException(e, "Error getting notebook instance");
    }

    // dummy return, exception thrown and control never reaches here
    return NotebookInstanceStatus.UNKNOWN_TO_SDK_VERSION;
  }

  /**
   * Start a AWS SageMaker Notebook
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void startSageMakerNotebook(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource) {
    SageMakerClient sageMakerClient =
        getSageMakerClient(awsCredentialsProvider, Region.of(notebookResource.getRegion()));

    logger.info("Starting notebook instance {}", notebookResource.getInstanceName());

    try {
      SdkHttpResponse httpResponse =
          sageMakerClient
              .startNotebookInstance(
                  StartNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookResource.getInstanceName())
                      .build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error starting notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e, "Error starting notebook instance");
    }
  }

  /**
   * Stop a AWS SageMaker Notebook
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void stopSageMakerNotebook(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource) {
    SageMakerClient sageMakerClient =
        getSageMakerClient(awsCredentialsProvider, Region.of(notebookResource.getRegion()));

    logger.info("Stopping notebook instance {}", notebookResource.getInstanceName());

    try {
      SdkHttpResponse httpResponse =
          sageMakerClient
              .stopNotebookInstance(
                  StopNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookResource.getInstanceName())
                      .build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error stopping notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e, "Error stopping notebook instance");
    }
  }

  /**
   * Delete a AWS SageMaker Notebook
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void deleteSageMakerNotebook(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource) {
    SageMakerClient sageMakerClient =
        getSageMakerClient(awsCredentialsProvider, Region.of(notebookResource.getRegion()));

    logger.info("Deleting notebook instance {}", notebookResource.getInstanceName());

    try {
      SdkHttpResponse httpResponse =
          sageMakerClient
              .deleteNotebookInstance(
                  DeleteNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookResource.getInstanceName())
                      .build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error deleting notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e, "Error deleting notebook instance", /* ignoreNotFound= */ true);
    }
  }

  /**
   * Wait for a AWS SageMaker Notebook status
   *
   * @param awsCredentialsProvider {@link AwsCredentialsProvider}
   * @param notebookResource {@link ControlledAwsSageMakerNotebookResource}
   * @param desiredStatus {@link NotebookInstanceStatus}
   * @throws ApiException ApiException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void waitForSageMakerNotebookStatus(
      AwsCredentialsProvider awsCredentialsProvider,
      ControlledAwsSageMakerNotebookResource notebookResource,
      NotebookInstanceStatus desiredStatus) {
    Region region = Region.of(notebookResource.getRegion());
    SageMakerWaiter sageMakerWaiter =
        getSageMakerWaiter(getSageMakerClient(awsCredentialsProvider, region));

    logger.info(
        "Waiting on notebook with name {}, desired status {}.",
        notebookResource.getInstanceName(),
        desiredStatus);

    DescribeNotebookInstanceRequest describeRequest =
        DescribeNotebookInstanceRequest.builder()
            .notebookInstanceName(notebookResource.getInstanceName())
            .build();

    try {
      WaiterResponse<DescribeNotebookInstanceResponse> waiterResponse =
          switch (desiredStatus) {
            case IN_SERVICE -> sageMakerWaiter.waitUntilNotebookInstanceInService(describeRequest);
            case STOPPED -> sageMakerWaiter.waitUntilNotebookInstanceStopped(describeRequest);
            case DELETING -> sageMakerWaiter.waitUntilNotebookInstanceDeleted(describeRequest);
            default -> throw new BadRequestException(
                "Can only wait for notebook InService, Stopped or Deleting");
          };

      ResponseOrException<DescribeNotebookInstanceResponse> responseOrException =
          waiterResponse.matched();
      if (responseOrException.exception().isPresent()) {
        Throwable t = responseOrException.exception().get();
        if (t instanceof SdkException e) {
          checkException(e, "Error polling notebook instance status");
        }
      }

    } catch (NotFoundException e) {
      // Not an error if waiting on deleted resource
      if (desiredStatus != NotebookInstanceStatus.DELETING) {
        throw e;
      }

    } catch (SdkException e) {
      checkException(e, "Error waiting for desired AWS SageMaker Notebook status");
    }
  }

  public static void checkException(SdkException ex, String altMessage) {
    checkException(ex, altMessage, /* ignoreNotFound= */ false);
  }

  /**
   * Check AWS SdkException and rethrow appropriate terra-friendly exception
   *
   * @param ex {@link SdkException}
   * @param altMessage alternate message for ApiException
   * @param ignoreNotFound if true, do not rethrow if NotFoundException
   * @throws NotFoundException NotFoundException
   * @throws UnauthorizedException UnauthorizedException
   * @throws BadRequestException BadRequestException
   */
  public static void checkException(SdkException ex, String altMessage, boolean ignoreNotFound)
      throws NotFoundException, UnauthorizedException, BadRequestException, ApiException {
    String message = ex.getMessage();
    if (message.contains("ResourceNotFoundException")
        || message.contains("RecordNotFound")
        || message.contains("does not exist")) {
      if (ignoreNotFound) {
        return;
      }
      throw new NotFoundException("Resource deleted or no longer accessible", ex);

    } else if (message.contains("not authorized to perform")) {
      throw new UnauthorizedException(
          "Error performing resource operation, check the name / permissions and retry", ex);

    } else if (message.contains("Unable to transition to")) {
      throw new BadRequestException("Unable to perform resource lifecycle operation", ex);
    }

    throw new ApiException(altMessage, ex);
  }

  /**
   * Delete the Security Group associated with a Workspace.
   *
   * @param crlClientConfig CRL client configuration
   * @param credentialsProvider Credentials provider for the WSM IAM role
   * @param workspaceUuid UUID of the Workspace associated with the Security Group
   * @param awsRegion AWS Region the Security Group exists in
   * @param securityGroupId ID of Security Group to delete
   * @throws {@link NoSuchElementException} if a Security Group with the passed ID does not exist in
   *     the passed region
   */
  public static void deleteWorkspaceSecurityGroup(
      ClientConfig crlClientConfig,
      AwsCredentialsProvider credentialsProvider,
      UUID workspaceUuid,
      Region awsRegion,
      String securityGroupId) {
    try (EC2SecurityGroupCow regionCow =
        EC2SecurityGroupCow.instanceOf(crlClientConfig, credentialsProvider, awsRegion)) {
      regionCow.delete(securityGroupId);

      logger.info(
          "Deleted Security Group ID '{}' for Workspace {} (Landing Zone {})",
          securityGroupId,
          workspaceUuid.toString(),
          awsRegion.toString());

    } catch (Ec2Exception e) {
      if (e.awsErrorDetails().errorCode().equals("InvalidGroup.NotFound")) {
        throw new NoSuchElementException(
            String.format("Security Group ID %s not found.", securityGroupId));
      } else {
        throw e;
      }
    }
  }
}
