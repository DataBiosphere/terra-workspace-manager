package bio.terra.workspace.common.utils;

import bio.terra.workspace.service.workspace.exceptions.SaCredentialsMissingException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.Tag;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Collection;
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

  public static Credentials assumeUserRole(
      AwsCloudContext awsCloudContext,
      Credentials serviceCredentials,
      String userEmail,
      Collection<Tag> tags,
      Integer duration) {
    AssumeRoleRequest request = new AssumeRoleRequest();
    request.setDurationSeconds(duration);
    request.setRoleArn(awsCloudContext.getUserRoleArn().toString());
    request.setRoleSessionName(userEmail);
    request.setTags(tags);

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
      String userEmail,
      Collection<Tag> tags,
      Integer duration) {
    Credentials serviceCredentials =
        assumeServiceRole(awsCloudContext, idToken, serviceEmail, MIN_TOKEN_DURATION_SECONDS);
    return assumeUserRole(awsCloudContext, serviceCredentials, userEmail, tags, duration);
  }
}
