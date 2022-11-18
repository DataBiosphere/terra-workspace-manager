package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.ShortUUID;
import bio.terra.workspace.service.workspace.exceptions.SaCredentialsMissingException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.sagemaker.AmazonSageMaker;
import com.amazonaws.services.sagemaker.AmazonSageMakerClientBuilder;
import com.amazonaws.services.sagemaker.model.CreateNotebookInstanceRequest;
import com.amazonaws.services.sagemaker.model.InstanceType;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.Tag;
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
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsUtils {
  private static final Logger logger = LoggerFactory.getLogger(AwsUtils.class);
  public static final Integer MIN_TOKEN_DURATION_SECONDS = 900;
  public static final Integer MAX_TOKEN_DURATION_SECONDS = 3600;

  public static Credentials assumeServiceRole(
      AwsCloudContext awsCloudContext, String idToken, String serviceEmail, Integer duration) {
    AssumeRoleWithWebIdentityRequest request = new AssumeRoleWithWebIdentityRequest();
    request.setDurationSeconds(duration);
    request.setRoleArn(awsCloudContext.getServiceRoleArn().toString());
    request.setRoleSessionName(serviceEmail);
    request.setWebIdentityToken(idToken);

    logger.info(
        String.format(
            "Assuming Service role ('%s') with session name `%s`, duration %d seconds.",
            request.getRoleArn().toString(),
            request.getRoleSessionName(),
            request.getDurationSeconds()));

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
    AWSSecurityTokenService securityTokenService =
        AWSSecurityTokenServiceClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
            .build();

    return securityTokenService.assumeRoleWithWebIdentity(request).getCredentials();
  }

  public static void addUserTags(Collection<Tag> tags, SamUser user) {
    tags.add(new Tag().withKey("user_email").withValue(user.getEmail()));
    tags.add(new Tag().withKey("user_id").withValue(user.getSubjectId()));
  }

  public static void addWorkspaceTags(Collection<Tag> tags, UUID workspaceUuid) {
    tags.add(new Tag().withKey("ws_id").withValue(workspaceUuid.toString()));
  }

  public static void addBucketTags(
      Collection<Tag> tags, String role, String s3BucketName, String prefix) {
    tags.add(new Tag().withKey("ws_role").withValue(role));
    tags.add(new Tag().withKey("s3_bucket").withValue(s3BucketName));
    tags.add(new Tag().withKey("terra_bucket").withValue(prefix));
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

    AssumeRoleRequest request = new AssumeRoleRequest();
    request.setDurationSeconds(duration);
    request.setRoleArn(awsCloudContext.getUserRoleArn().toString());
    request.setRoleSessionName(user.getEmail());
    request.setTags(userTags);

    logger.info(
        String.format(
            "Assuming User role ('%s') with session name `%s`, duration %d seconds, and tags: '%s'.",
            request.getRoleArn().toString(),
            request.getRoleSessionName(),
            request.getDurationSeconds(),
            request.getTags()));

    BasicSessionCredentials sessionCredentials =
        new BasicSessionCredentials(
            serviceCredentials.getAccessKeyId(),
            serviceCredentials.getSecretAccessKey(),
            serviceCredentials.getSessionToken());

    AWSSecurityTokenService securityTokenService =
        AWSSecurityTokenServiceClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
            .build();

    return securityTokenService.assumeRole(request).getCredentials();
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
    credentialMap.put("sessionId", userCredentials.getAccessKeyId());
    credentialMap.put("sessionKey", userCredentials.getSecretAccessKey());
    credentialMap.put("sessionToken", userCredentials.getSessionToken());

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

  private static AmazonS3 getS3Session(Credentials credentials) {
    BasicSessionCredentials sessionCredentials =
        new BasicSessionCredentials(
            credentials.getAccessKeyId(),
            credentials.getSecretAccessKey(),
            credentials.getSessionToken());

    return AmazonS3Client.builder()
        .withRegion(Regions.US_EAST_1)
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
        .build();
  }

  public static boolean checkFolderExistence(
      Credentials credentials, String bucketName, String folder) {
    AmazonS3 s3 = getS3Session(credentials);
    String prefix = String.format("%s/", folder);

    ListObjectsV2Request request =
        new ListObjectsV2Request()
            .withBucketName(bucketName)
            .withPrefix(prefix)
            .withDelimiter("/")
            .withMaxKeys(1);

    ListObjectsV2Result result = s3.listObjectsV2(request);
    return result.getKeyCount() > 0;
  }

  public static void createFolder(Credentials credentials, String bucketName, String folder) {
    // Creating a "folder" requires writing an empty object ending with the delimiter ('/').
    AmazonS3 s3 = getS3Session(credentials);
    String folderKey = String.format("%s/", folder);
    s3.putObject(bucketName, folderKey, "");
  }

  public static void undoCreateFolder(Credentials credentials, String bucketName, String folder) {
    AmazonS3 s3 = getS3Session(credentials);
    String folderKey = String.format("%s/", folder);
    DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, folderKey);
    s3.deleteObject(deleteObjectRequest);
  }

  private static AmazonSageMaker getSagemakerSession(Credentials credentials, Regions region) {
    BasicSessionCredentials sessionCredentials =
        new BasicSessionCredentials(
            credentials.getAccessKeyId(),
            credentials.getSecretAccessKey(),
            credentials.getSessionToken());

    return AmazonSageMakerClientBuilder.standard()
        .withRegion(region)
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
        .build();
  }

  // TODO: Can we avoid this with generics??
  private static Collection<com.amazonaws.services.sagemaker.model.Tag> toSagemakerTags(
      Collection<Tag> stsTags) {
    Collection<com.amazonaws.services.sagemaker.model.Tag> tags = new HashSet<>();
    for (Tag stsTag : stsTags) {
      tags.add(
          new com.amazonaws.services.sagemaker.model.Tag()
              .withKey(stsTag.getKey())
              .withValue(stsTag.getValue()));
    }
    return tags;
  }

  public static void createSageMakerNotebook(
      AwsCloudContext awsCloudContext,
      Credentials credentials,
      UUID workspaceUuid,
      SamUser user,
      Regions region,
      InstanceType instanceType,
      String notebookName) {
    AmazonSageMaker sageMaker = getSagemakerSession(credentials, region);

    Collection<Tag> tags = new HashSet<>();
    addUserTags(tags, user);
    addWorkspaceTags(tags, workspaceUuid);

    CreateNotebookInstanceRequest request =
        new CreateNotebookInstanceRequest()
            .withNotebookInstanceName(notebookName)
            .withInstanceType(instanceType)
            .withRoleArn(awsCloudContext.getUserRoleArn().toString())
            .withTags(toSagemakerTags(tags));

    sageMaker.createNotebookInstance(request);
  }

  public static String generateUniquePrefix() {
    return ShortUUID.get();
  }
}
