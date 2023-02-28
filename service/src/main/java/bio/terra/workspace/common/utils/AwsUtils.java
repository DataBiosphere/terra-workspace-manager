package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.NotFoundException;
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
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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

  // TODO(TERRA-384) - move to COW in TCL
  private static final int MAX_ROLE_SESSION_NAME_LENGTH = 64;
  private static final Duration SAGEMAKER_NOTEBOOK_WAITER_TIMEOUT_DURATION =
      Duration.ofSeconds(900);
  private static final Set<NotebookInstanceStatus> startableStatusSet =
      Set.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED);
  private static final Set<NotebookInstanceStatus> stoppableStatusSet =
      Set.of(NotebookInstanceStatus.IN_SERVICE);
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
            .roleArn(awsCloudContext.getServiceRoleArn().toString())
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
        logger.info(String.format("JWT 'aud' claim: '%s'", jwtClaimsSet.getAudience()));
        logger.info(String.format("JWT 'azp' claim: '%s'", jwtClaimsSet.getStringClaim("azp")));
        logger.info(String.format("JWT 'sub' claim: '%s'", jwtClaimsSet.getStringClaim("sub")));
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
    tags.add(Tag.builder().key("user_email").value(user.getEmail()).build());
    tags.add(Tag.builder().key("user_id").value(user.getSubjectId()).build());
  }

  public static void addWorkspaceTags(Collection<Tag> tags, UUID workspaceUuid) {
    tags.add(Tag.builder().key("ws_id").value(workspaceUuid.toString()).build());
  }

  public static void addBucketTags(
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
            .roleArn(awsCloudContext.getUserRoleArn().toString())
            .roleSessionName(getRoleSessionName(user.getEmail()))
            .tags(userTags)
            .build();

    logger.info(
        String.format(
            "Assuming User role ('%s') with session name `%s`, duration %d seconds, and tags: '%s'.",
            request.roleArn(),
            request.roleSessionName(),
            request.durationSeconds(),
            request.tags()));

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

  public static boolean checkFolderExistence(
      Credentials credentials, Region region, String bucketName, String folder) {
    S3Client s3 = getS3Session(credentials, region);
    String prefix = String.format("%s/", folder);

    ListObjectsV2Request request =
        ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .delimiter("/")
            .maxKeys(1)
            .build();

    ListObjectsV2Response result = s3.listObjectsV2(request);
    return result.keyCount() > 0;
  }

  public static void createFolder(
      Credentials credentials, Region region, String bucketName, String folder) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    String folderKey = String.format("%s/", folder);
    putObject(credentials, region, bucketName, folderKey, "");
  }

  public static void undoCreateFolder(
      Credentials credentials, Region region, String bucketName, String folder) {
    String folderKey = String.format("%s/", folder);
    deleteObject(credentials, region, bucketName, folderKey);
  }

  public static void putObject(
      Credentials credentials, Region region, String bucketName, String key, String content) {
    S3Client s3 = getS3Session(credentials, region);
    s3.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(key).build(),
        RequestBody.fromString(content));
  }

  public static void deleteObject(
      Credentials credentials, Region region, String bucketName, String key) {
    S3Client s3 = getS3Session(credentials, region);
    DeleteObjectRequest deleteObjectRequest =
        DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
    s3.deleteObject(deleteObjectRequest);
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
    Collection<software.amazon.awssdk.services.sagemaker.model.Tag> tags = new HashSet<>();
    for (Tag stsTag : stsTags) {
      tags.add(
          software.amazon.awssdk.services.sagemaker.model.Tag.builder()
              .key(stsTag.key())
              .value(stsTag.value())
              .build());
    }
    return tags;
  }

  public static void createSageMakerNotebook(
      AwsCloudContext awsCloudContext,
      Credentials credentials,
      UUID workspaceUuid,
      SamUser user,
      Region region,
      InstanceType instanceType,
      String notebookName) {
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
            .roleArn(awsCloudContext.getUserRoleArn().toString())
            .kmsKeyId(awsCloudContext.getKmsKeyArn().resource().resource())
            .tags(toSagemakerTags(tags));

    if (awsCloudContext.getNotebookLifecycleConfigArn() != null) {
      String policyName = awsCloudContext.getNotebookLifecycleConfigArn().resource().resource();
      logger.info(
          String.format(
              "Attaching lifecycle policy '%s' to notebook '%s'.", policyName, notebookName));
      requestBuilder.lifecycleConfigName(policyName);
    }

    sageMaker.createNotebookInstance(requestBuilder.build());
  }

  public static void waitForSageMakerNotebookStatus(
      Credentials credentials,
      Region region,
      String notebookName,
      Optional<NotebookInstanceStatus> desiredStatus) {
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
    if (desiredStatus.isEmpty()) { // DELETED
      waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceDeleted(describeRequest);
    } else if (desiredStatus.get() == NotebookInstanceStatus.IN_SERVICE) {
      waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceInService(describeRequest);
    } else if (desiredStatus.get() == NotebookInstanceStatus.STOPPED) {
      waiterResponse = sageMakerWaiter.waitUntilNotebookInstanceStopped(describeRequest);
    } else {
      throw new BadRequestException("Can only wait for notebook InService, Stopped or Deleted");
    }

    ResponseOrException<DescribeNotebookInstanceResponse> responseOrException =
        waiterResponse.matched();
    if (responseOrException.response().isPresent()) {
      checkNotebookStatus(
          responseOrException.response().get().notebookInstanceStatus(), startableStatusSet);
      return; // success

    } else if (responseOrException.exception().isPresent()) {
      Throwable t = responseOrException.exception().get();
      if (t instanceof Exception) {
        checkException((Exception) t);
      }
      logger.error("Error polling notebook instance status: " + t);
    }

    throw new ApiException("Error checking notebook instance status");
  }

  public static void stopSageMakerNotebook(
      Credentials credentials, Region region, String notebookName) {
    // TODO(TERRA-384) - move to COW in TCL
    try {
      SageMakerClient sageMaker = getSagemakerSession(credentials, region);
      DescribeNotebookInstanceRequest describeRequest =
          DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build();

      NotebookInstanceStatus notebookStatus =
          sageMaker.describeNotebookInstance(describeRequest).notebookInstanceStatus();
      if (startableStatusSet.contains(notebookStatus)) {
        logger.info(
            String.format(
                "SageMaker notebook instance in status %s, no stop needed.", notebookStatus));
        return;
      }

      checkNotebookStatus(notebookStatus, stoppableStatusSet);
      logger.info(
          String.format("Stopping SageMaker notebook instance with name '%s'.", notebookName));

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
    // TODO(TERRA-384) - move to COW in TCL
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
      checkNotebookStatus(describeResponse.notebookInstanceStatus(), deletableStatusSet);
      logger.info(
          String.format("Deleting SageMaker notebook instance with name '%s'.", notebookName));

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

  private static void checkNotebookStatus(
      NotebookInstanceStatus currentStatus, Set<NotebookInstanceStatus> expectedStatusSet) {
    // TODO(TERRA-384) - move to COW in TCL
    if (!expectedStatusSet.contains(currentStatus)) {
      throw new ApiException(
          "Expected notebook instance status is "
              + expectedStatusSet
              + " but current status is "
              + currentStatus);
    }
  }

  private static void checkException(Exception ex) {
    // TODO(TERRA-384) - move to COW in TCL
    if (ex instanceof SdkException) {
      String message = ex.getMessage();
      if (message.contains("not authorized to perform")) {
        throw new NotFoundException(
            "Error performing notebook operation, check the instance name / permissions and retry",
            ex);
      } else if (message.contains("Unable to transition to")) {
        throw new BadRequestException("Unable to perform notebook operation on cloud platform", ex);
      }
    } else if (ex instanceof ErrorReportException) {
      throw (ErrorReportException) ex;
    }
  }
}
