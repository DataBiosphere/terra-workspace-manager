package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.exception.ValidationException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.service.workspace.exceptions.SaCredentialsMissingException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
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
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.*;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.Tag;

public class AwsUtils {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtils.class);
  public static final int MIN_TOKEN_DURATION_SECONDS = 900;
  private static final int MAX_ROLE_SESSION_NAME_LENGTH = 64;

  private static final int S3_BUCKET_MAX_OBJECTS_PER_REQUEST = 1000;
  private static final Duration SAGEMAKER_NOTEBOOK_WAITER_TIMEOUT_DURATION =
      Duration.ofSeconds(900);
  private static final Set<NotebookInstanceStatus> startableStatusSet =
      Set.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED);
  private static final Set<NotebookInstanceStatus> stoppableStatusSet =
      Set.of(NotebookInstanceStatus.IN_SERVICE, NotebookInstanceStatus.FAILED);
  private static final Set<NotebookInstanceStatus> deletableStatusSet =
      Set.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED);

  private static String getRoleSessionName(String name) {
    return (name.length() > MAX_ROLE_SESSION_NAME_LENGTH)
        ? name.substring(0, MAX_ROLE_SESSION_NAME_LENGTH - 1)
        : name;
  }

  public static Credentials assumeServiceRole(
      AwsCloudContext awsCloudContext, String idToken, String serviceEmail, Integer duration) {
    AssumeRoleWithWebIdentityRequest request =
        AssumeRoleWithWebIdentityRequest.builder()
            .durationSeconds(duration)
            .roleArn(awsCloudContext.getServiceRoleArn())
            .roleSessionName(getRoleSessionName(serviceEmail))
            .webIdentityToken(idToken)
            .build();

    logger.info(
        String.format(
            "Assuming Service role ('%s') with session name `%s`, duration %d seconds.",
            request.roleArn(), request.roleSessionName(), request.durationSeconds()));

    if (logger.isInfoEnabled()) {
      try {
        JWT jwt = JWTParser.parse(idToken);
        JWTClaimsSet jwtClaimsSet = jwt.getJWTClaimsSet();
        logger.info("JWT 'aud' claim: '{}'", jwtClaimsSet.getAudience());
        logger.info("JWT 'azp' claim: '{}'", jwtClaimsSet.getStringClaim("azp"));
        logger.info("JWT 'sub' claim: '{}'", jwtClaimsSet.getStringClaim("sub"));
      } catch (ParseException e) {
        throw new SaCredentialsMissingException(
            String.format("Passed SA credential is not a valid JWT: '%s'", e.getMessage()));
      }
    }
    StsClient securityTokenService =
        StsClient.builder()
            .region(Region.AWS_GLOBAL) // STS not regional but API requires
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .build();

    return securityTokenService.assumeRoleWithWebIdentity(request).credentials();
  }

  public enum RoleTag {
    READER("reader"),
    WRITER("writer");

    private final String value;

    RoleTag(String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }
  }

  public static void addUserTags(Collection<Tag> tags, SamUser user) {
    if (user != null) {
      tags.add(Tag.builder().key("user_email").value(user.getEmail()).build());
      tags.add(Tag.builder().key("user_id").value(user.getSubjectId()).build());
    }
  }

  public static void addWorkspaceTags(Collection<Tag> tags, UUID workspaceUuid) {
    if (workspaceUuid != null) {
      tags.add(Tag.builder().key("ws_id").value(workspaceUuid.toString()).build());
    }
  }

  public static void addBucketTagsForRole(
      Collection<Tag> tags, RoleTag role, String s3BucketName, String prefix) {
    tags.add(Tag.builder().key("ws_role").value(role.getValue()).build());
    tags.add(Tag.builder().key("s3_bucket").value(s3BucketName).build());
    tags.add(Tag.builder().key("terra_bucket").value(prefix).build());
  }

  public static Credentials assumeUserRole(
      AwsCloudContext awsCloudContext,
      Credentials serviceCredentials,
      SamUser user,
      Collection<Tag> tags,
      Integer duration) {
    addUserTags(tags, user);
    HashSet<Tag> userTags = new HashSet<>(tags);

    AssumeRoleRequest request =
        AssumeRoleRequest.builder()
            .durationSeconds(duration)
            .roleArn(awsCloudContext.getUserRoleArn())
            .roleSessionName(getRoleSessionName(user.getEmail()))
            .tags(userTags)
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

    StsClient securityTokenService =
        StsClient.builder()
            .region(Region.AWS_GLOBAL) // STS not regional but API requires
            .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
            .build();

    return securityTokenService.assumeRole(request).credentials();
  }

  public static Credentials assumeUserRole(
      AwsCloudContext awsCloudContext,
      String idToken,
      String serviceEmail,
      SamUser user,
      Collection<Tag> tags,
      Integer duration) {
    Credentials serviceCredentials =
        assumeServiceRole(awsCloudContext, idToken, serviceEmail, MIN_TOKEN_DURATION_SECONDS);
    return assumeUserRole(awsCloudContext, serviceCredentials, user, tags, duration);
  }

  public static URL createConsoleUrl(
      Credentials userCredentials, Integer duration, URL destination) {

    Map<String, String> credentialMap = new HashMap<>();
    credentialMap.put("sessionId", userCredentials.accessKeyId());
    credentialMap.put("sessionKey", userCredentials.secretAccessKey());
    credentialMap.put("sessionToken", userCredentials.sessionToken());

    try {
      URI uri =
          new URIBuilder()
              .setScheme("https")
              .setHost("signin.aws.amazon.com")
              .setPath("federation")
              .setParameter("Action", "getSigninToken")
              .setParameter("DurationSeconds", duration.toString())
              .setParameter("SessionType", "json")
              .setParameter("Session", new JSONObject(credentialMap).toString())
              .build();

      URL getTokenUrl = uri.toURL();

      URLConnection urlConnection = getTokenUrl.openConnection();

      BufferedReader bufferReader =
          new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
      String urlSigninToken = new JSONObject(bufferReader.readLine()).getString("SigninToken");
      bufferReader.close();

      uri =
          new URIBuilder()
              .setScheme("https")
              .setHost("signin.aws.amazon.com")
              .setPath("federation")
              .setParameter("Action", "login")
              .setParameter("Issuer", "terra.verily.com")
              .setParameter("Destination", destination.toString())
              .setParameter("SigninToken", urlSigninToken)
              .build();

      return uri.toURL();

    } catch (Exception e) {
      throw new ApiException("Failed to get URL.", e);
    }
  }

  private static S3Client getS3Session(Credentials credentials, Region region) {
    AwsSessionCredentials sessionCredentials =
        AwsSessionCredentials.create(
            credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());

    return S3Client.builder()
        .region(region)
        .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
        .build();
  }

  // TODO: Can we avoid this with generics??
  private static Collection<software.amazon.awssdk.services.s3.model.Tag> toS3Tags(
      Collection<Tag> stsTags) {
    return stsTags.stream()
        .map(
            stsTag ->
                software.amazon.awssdk.services.s3.model.Tag.builder()
                    .key(stsTag.key())
                    .value(stsTag.value())
                    .build())
        .collect(Collectors.toCollection(HashSet::new));
  }

  public static boolean checkFolderExistence(
      Credentials credentials, Region region, String bucketName, String folder) {
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    return CollectionUtils.isNotEmpty(
        getObjectKeysByPrefix(credentials, region, bucketName, folderKey, 1));
  }

  public static void createFolder(
      Credentials credentials,
      UUID workspaceUuid,
      SamUser user,
      Region region,
      String bucketName,
      String folder) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    putObject(credentials, workspaceUuid, user, region, bucketName, folderKey, "");
  }

  public static void deleteFolder(
      Credentials credentials, Region region, String bucketName, String folder) {
    String folderKey = folder.endsWith("/") ? folder : String.format("%s/", folder);
    List<String> objectKeys =
        getObjectKeysByPrefix(credentials, region, bucketName, folderKey, Integer.MAX_VALUE);
    deleteObjects(credentials, region, bucketName, objectKeys);
  }

  public static void putObject(
      Credentials credentials,
      UUID workspaceUuid,
      SamUser user,
      Region region,
      String bucketName,
      String key,
      String content) {
    try {
      S3Client s3 = getS3Session(credentials, region);

      Collection<Tag> tags = new HashSet<>();
      addUserTags(tags, user);
      addWorkspaceTags(tags, workspaceUuid);

      if (key.endsWith("/")) { // folder
        logger.info("Creating S3 folder object with name '{}' and key '{}'.", bucketName, key);
      } else {
        logger.info(
            "Creating S3 object with name '{}', key '{}' and {} content.",
            bucketName,
            key,
            StringUtils.isEmpty(content) ? "empty" : "");
      }

      PutObjectRequest.Builder requestBuilder =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .tagging(Tagging.builder().tagSet(toS3Tags(tags)).build());

      SdkHttpResponse httpResponse =
          s3.putObject(requestBuilder.build(), RequestBody.fromString(content)).sdkHttpResponse();
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

  public static List<String> getObjectKeysByPrefix(
      Credentials credentials, Region region, String bucketName, String prefix, int limit) {
    try {
      S3Client s3 = getS3Session(credentials, region);

      ListObjectsV2Request.Builder requestBuilder =
          ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).delimiter("/");

      int limitRemaining = limit;
      String continuationToken = null;
      List<String> objectKeys = new ArrayList<>();
      while (limitRemaining > 0) {
        int curLimit = Math.min(S3_BUCKET_MAX_OBJECTS_PER_REQUEST, limitRemaining);
        limitRemaining -= curLimit;

        ListObjectsV2Response listResponse =
            s3.listObjectsV2(
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

  public static void deleteObject(
      Credentials credentials, Region region, String bucketName, String key) {
    if (key.endsWith("/")) {
      deleteFolder(credentials, region, bucketName, key);
    }
    deleteObjects(credentials, region, bucketName, List.of(key));
  }

  public static void deleteObjects(
      Credentials credentials, Region region, String bucketName, List<String> keys) {
    try {
      S3Client s3 = getS3Session(credentials, region);
      DeleteObjectsRequest.Builder deleteRequestBuilder =
          DeleteObjectsRequest.builder().bucket(bucketName);

      ListUtils.partition(keys, S3_BUCKET_MAX_OBJECTS_PER_REQUEST)
          .forEach(
              keysList -> {
                logger.info("Deleting storage objects with keys {}.", keysList);

                Collection<ObjectIdentifier> objectIds =
                    keysList.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList();

                DeleteObjectsResponse deleteResponse =
                    s3.deleteObjects(
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
                deleteResponse
                    .errors()
                    .forEach(
                        s3Error -> logger.warn("Failed to delete storage objects: {}", s3Error));
              });

    } catch (SdkException e) {
      checkException(e);
      throw new ApiException("Error deleting storage objects", e);
    }
  }

  private static SageMakerClient getSagemakerSession(Credentials credentials, Region region) {
    AwsSessionCredentials sessionCredentials =
        AwsSessionCredentials.create(
            credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());

    return SageMakerClient.builder()
        .region(region)
        .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
        .build();
  }

  // TODO: Can we avoid this with generics??
  private static Collection<software.amazon.awssdk.services.sagemaker.model.Tag> toSagemakerTags(
      Collection<Tag> stsTags) {
    return stsTags.stream()
        .map(
            stsTag ->
                software.amazon.awssdk.services.sagemaker.model.Tag.builder()
                    .key(stsTag.key())
                    .value(stsTag.value())
                    .build())
        .collect(Collectors.toCollection(HashSet::new));
  }

  public static void createSageMakerNotebook(
      AwsCloudContext awsCloudContext,
      Credentials credentials,
      UUID workspaceUuid,
      SamUser user,
      Region region,
      InstanceType instanceType,
      String notebookName) {
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);

      Collection<Tag> tags = new HashSet<>();
      addUserTags(tags, user);
      addWorkspaceTags(tags, workspaceUuid);

      logger.info(
          String.format(
              "Creating SageMaker notebook instance with name '%s' and type '%s'.",
              notebookName, instanceType));

      CreateNotebookInstanceRequest.Builder requestBuilder =
          CreateNotebookInstanceRequest.builder()
              .notebookInstanceName(notebookName)
              .instanceType(instanceType)
              .roleArn(awsCloudContext.getUserRoleArn())
              .kmsKeyId(Arn.fromString(awsCloudContext.getKmsKeyArn()).resource().resource())
              .tags(toSagemakerTags(tags));

      if (awsCloudContext.getNotebookLifecycleConfigArn() != null) {
        String policyName =
            Arn.fromString(awsCloudContext.getNotebookLifecycleConfigArn()).resource().resource();
        logger.info(
            String.format(
                "Attaching lifecycle policy '%s' to notebook '%s'.", policyName, notebookName));
        requestBuilder.lifecycleConfigName(policyName);
      }

      SdkHttpResponse httpResponse =
          sageMaker.createNotebookInstance(requestBuilder.build()).sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error creating notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e);
      throw new ApiException("Error creating notebook instance", e);
    }
  }

  public static void stopSageMakerNotebook(
      Credentials credentials, Region region, String notebookName) {
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);
      DescribeNotebookInstanceRequest describeRequest =
          DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build();

      NotebookInstanceStatus notebookStatus =
          sageMaker.describeNotebookInstance(describeRequest).notebookInstanceStatus();
      if (startableStatusSet.contains(notebookStatus)) {
        logger.info("SageMaker notebook instance in status {}, no stop needed.", notebookStatus);
        return;
      }

      checkNotebookStatusAndThrow(notebookStatus, stoppableStatusSet);
      logger.info("Stopping SageMaker notebook instance with name '{}'.", notebookName);

      SdkHttpResponse httpResponse =
          sageMaker
              .stopNotebookInstance(
                  StopNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error stopping notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e);
      throw new ApiException("Error stopping notebook instance", e);
    }
  }

  public static void deleteSageMakerNotebook(
      Credentials credentials, Region region, String notebookName) {
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);
      DescribeNotebookInstanceRequest describeRequest =
          DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build();

      DescribeNotebookInstanceResponse describeResponse =
          sageMaker.describeNotebookInstance(describeRequest);
      SdkHttpResponse describeHttpResponse = describeResponse.sdkHttpResponse();
      if (!describeHttpResponse.isSuccessful()) {
        throw new ApiException(
            "Error fetching notebook instance, "
                + describeHttpResponse
                    .statusText()
                    .orElse(String.valueOf(describeHttpResponse.statusCode())));
      }

      // must be stopped or failed. AWS throws error if notebook is not found
      checkNotebookStatusAndThrow(describeResponse.notebookInstanceStatus(), deletableStatusSet);
      logger.info("Deleting SageMaker notebook instance with name '{}'.", notebookName);

      SdkHttpResponse httpResponse =
          sageMaker
              .deleteNotebookInstance(
                  DeleteNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookName)
                      .build())
              .sdkHttpResponse();
      if (!httpResponse.isSuccessful()) {
        throw new ApiException(
            "Error deleting notebook instance, "
                + httpResponse.statusText().orElse(String.valueOf(httpResponse.statusCode())));
      }

    } catch (SdkException e) {
      checkException(e);
      throw new ApiException("Error deleting notebook instance", e);
    }
  }

  public static void waitForSageMakerNotebookStatus(
      Credentials credentials,
      Region region,
      String notebookName,
      NotebookInstanceStatus desiredStatus) {
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);
      SageMakerWaiter sageMakerWaiter =
          SageMakerWaiter.builder()
              .client(sageMaker)
              .overrideConfiguration(
                  WaiterOverrideConfiguration.builder()
                      .waitTimeout(SAGEMAKER_NOTEBOOK_WAITER_TIMEOUT_DURATION)
                      .build())
              .build();

      DescribeNotebookInstanceRequest describeRequest =
          DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build();
      WaiterResponse<DescribeNotebookInstanceResponse> waiterResponse;
      if (desiredStatus == null) { // DELETED
        waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceDeleted(describeRequest);
      } else if (desiredStatus == NotebookInstanceStatus.IN_SERVICE) {
        waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceInService(describeRequest);
      } else if (desiredStatus == NotebookInstanceStatus.STOPPED) {
        waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceStopped(describeRequest);
      } else {
        throw new BadRequestException("Can only wait for notebook InService, Stopped or Deleted");
      }

      ResponseOrException<DescribeNotebookInstanceResponse> responseOrException =
          waiterResponse.matched();
      if (responseOrException.exception().isPresent()) {
        Throwable t = responseOrException.exception().get();
        if (t instanceof Exception) {
          checkException((Exception) t);
        }
        logger.error("Error polling notebook instance status", t);

      } else if (responseOrException.response().isPresent()) {
        checkNotebookStatusAndThrow(
            responseOrException.response().get().notebookInstanceStatus(),
            Stream.of(desiredStatus).collect(Collectors.toSet()));
      }

    } catch (NotFoundException e) {
      // Waiting on deleted resource may result in this, not an error
      if (desiredStatus != null) {
        throw e;
      }

    } catch (Exception e) {
      checkException(e);
      throw new ApiException("Error waiting for notebook instance", e);
    }
  }

  public static URL getSageMakerNotebookProxyUrl(
      Credentials credentials, Region region, String notebookName, Integer duration, String view) {
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);

      NotebookInstanceStatus notebookStatus =
          sageMaker
              .describeNotebookInstance(
                  DescribeNotebookInstanceRequest.builder()
                      .notebookInstanceName(notebookName)
                      .build())
              .notebookInstanceStatus();
      if (notebookStatus != NotebookInstanceStatus.IN_SERVICE) {
        throw new BadRequestException(
            String.format(
                "ProxyUrl only available for %s notebooks, current status is %s",
                NotebookInstanceStatus.IN_SERVICE, notebookStatus.toString()));
      }

      CreatePresignedNotebookInstanceUrlRequest request =
          CreatePresignedNotebookInstanceUrlRequest.builder()
              .notebookInstanceName(notebookName)
              .sessionExpirationDurationInSeconds(duration)
              .build();

      CreatePresignedNotebookInstanceUrlResponse result =
          sageMaker.createPresignedNotebookInstanceUrl(request);

      return new URIBuilder(result.authorizedUrl()).addParameter("view", view).build().toURL();

    } catch (Exception e) {
      checkException(e);
      throw new ApiException("Failed to get proxy uri.", e);
    }
  }

  private static void checkNotebookStatusAndThrow(
      NotebookInstanceStatus currentStatus, Set<NotebookInstanceStatus> expectedStatusSet)
      throws ValidationException {
    if (!expectedStatusSet.contains(currentStatus)) {
      throw new ValidationException(
          String.format(
              "Expected notebook instance status is %s but current status is %s",
              expectedStatusSet, currentStatus));
    }
  }

  private static void checkException(Exception ex)
      throws NotFoundException, UnauthorizedException, BadRequestException {
    if (ex instanceof SdkException) {
      String message = ex.getMessage();
      if (message.contains("ResourceNotFoundException") || message.contains("RecordNotFound")) {
        throw new NotFoundException("Resource deleted or no longer accessible", ex);

      } else if (message.contains("not authorized to perform")) {
        throw new UnauthorizedException(
            "Error performing resource operation, check the name / permissions and retry", ex);

      } else if (message.contains("Unable to transition to")) {
        throw new BadRequestException("Unable to perform resource lifecycle operation", ex);
      }

    } else if (ex instanceof ErrorReportException) {
      throw (ErrorReportException) ex;
    }
  }
}
