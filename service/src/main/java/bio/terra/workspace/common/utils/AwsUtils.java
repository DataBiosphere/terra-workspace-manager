package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.ShortUUID;
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
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.CreatePresignedNotebookInstanceUrlRequest;
import software.amazon.awssdk.services.sagemaker.model.CreatePresignedNotebookInstanceUrlResponse;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.InstanceType;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.Tag;

public class AwsUtils {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtils.class);
  public static final Integer MIN_TOKEN_DURATION_SECONDS = 900;

  public static Credentials assumeServiceRole(
      AwsCloudContext awsCloudContext, String idToken, String serviceEmail, Integer duration) {
    AssumeRoleWithWebIdentityRequest request =
        AssumeRoleWithWebIdentityRequest.builder()
            .durationSeconds(duration)
            .roleArn(awsCloudContext.getServiceRoleArn().toString())
            .roleSessionName(serviceEmail)
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

  public static enum RoleTag {
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

    HashSet<Tag> userTags = new HashSet<>();
    addUserTags(tags, user);
    userTags.addAll(tags);

    AssumeRoleRequest request =
        AssumeRoleRequest.builder()
            .durationSeconds(duration)
            .roleArn(awsCloudContext.getUserRoleArn().toString())
            .roleSessionName(user.getEmail())
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
      String encodedCredential =
          URLEncoder.encode(new JSONObject(credentialMap).toString(), "UTF-8");

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

  public static String generateUniquePrefix() {
    return ShortUUID.get();
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

  public static void waitForSageMakerNotebookInService(
      Credentials credentials, Region region, String notebookName) {
    SageMakerClient sageMaker = getSagemakerSession(credentials, region);
    DescribeNotebookInstanceRequest request =
        DescribeNotebookInstanceRequest.builder().notebookInstanceName(notebookName).build();

    while (true) {
      DescribeNotebookInstanceResponse result = sageMaker.describeNotebookInstance(request);
      NotebookInstanceStatus status = result.notebookInstanceStatus();

      if (status.equals(NotebookInstanceStatus.IN_SERVICE)) {
        return;
      } else if (!status.equals(NotebookInstanceStatus.PENDING)) {
        throw new ApiException(
            String.format("Unexpected notebook state '%s' at creation time.", status));
      }

      try {
        logger.info(
            String.format(
                "Creating notebook '%s' waiting for '%s' status, current status is '%s'.",
                notebookName, NotebookInstanceStatus.IN_SERVICE, status));
        TimeUnit.SECONDS.sleep(30);
      } catch (InterruptedException e) {
        // Don't care...
      }
    }
  }

  public static URL getSageMakerNotebookProxyUrl(
      Credentials credentials, Region region, String notebookName, Integer duration, String view) {
    SageMakerClient sageMaker = getSagemakerSession(credentials, region);

    NotebookInstanceStatus notebookStatus =
        sageMaker
            .describeNotebookInstance(
                DescribeNotebookInstanceRequest.builder()
                    .notebookInstanceName(notebookName)
                    .build())
            .notebookInstanceStatus();
    if (notebookStatus != NotebookInstanceStatus.IN_SERVICE) {
      throw new ApiException(
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

    try {
      return new URIBuilder(result.authorizedUrl()).addParameter("view", view).build().toURL();

    } catch (Exception e) {
      throw new ApiException("Failed to get URL.", e);
    }
  }
}
