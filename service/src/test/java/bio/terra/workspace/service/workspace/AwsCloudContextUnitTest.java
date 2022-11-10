package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.arn.Arn;
import com.amazonaws.regions.Regions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AwsCloudContextUnitTest extends BaseAwsUnitTest {

  @Autowired private AwsConfiguration awsConfiguration;

  // This configuration must match the default configuration as defined in
  // services/src/test/resources/application-aws-unit-tests.yml
  static final String ACCOUNT_ID = "123456789012";
  static final String GOOGLE_JWT_AUDIENCE = "test_jwt_audience";
  static final Arn ROLE_ARN_SERVICE = getRoleArn(ACCOUNT_ID, "ServiceRole");
  static final Arn ROLE_ARN_USER = getRoleArn(ACCOUNT_ID, "UserRole");
  static final String BUCKET_NAME_US_EAST = "east-region-bucket";
  static final String BUCKET_NAME_US_WEST = "west-region-bucket";

  private static Arn getRoleArn(String accountId, String roleName) {
    return Arn.builder()
        .withPartition("aws")
        .withService("iam")
        .withRegion("") // Roles do not have a region; this must be blank, not null.
        .withAccountId(accountId)
        .withResource(roleName)
        .build();
  }

  private AwsCloudContext buildTestContext() {
    return AwsCloudContext.builder()
        .accountNumber(ACCOUNT_ID)
        .serviceRoleArn(ROLE_ARN_SERVICE)
        .serviceRoleAudience(GOOGLE_JWT_AUDIENCE)
        .userRoleArn(ROLE_ARN_USER)
        .addBucket(Regions.US_EAST_1, BUCKET_NAME_US_EAST)
        .addBucket(Regions.US_WEST_1, BUCKET_NAME_US_WEST)
        .build();
  }

  private void validateContext(AwsCloudContext compareContext) {
    assertEquals(ACCOUNT_ID, compareContext.getAccountNumber());
    assertEquals(ROLE_ARN_SERVICE, compareContext.getServiceRoleArn());
    assertEquals(GOOGLE_JWT_AUDIENCE, compareContext.getServiceRoleAudience());
    assertEquals(ROLE_ARN_USER, compareContext.getUserRoleArn());
    assertEquals(BUCKET_NAME_US_EAST, compareContext.getBucketNameForRegion(Regions.US_EAST_1));
    assertEquals(BUCKET_NAME_US_WEST, compareContext.getBucketNameForRegion(Regions.US_WEST_1));
    assertEquals(null, compareContext.getBucketNameForRegion(Regions.EU_CENTRAL_1));
  }

  @Test
  void basic() {
    AwsCloudContext testContext = buildTestContext();
    validateContext(testContext);
  }

  @Test
  void fromConfig() {
    // As noted above, the test configuration must match default landing zone as configured in
    // application-aws-unit-test.yml

    AwsConfiguration.AwsLandingZoneConfiguration landingZoneConfiguration = null;
    for (AwsConfiguration.AwsLandingZoneConfiguration landingZoneConfigurationIter :
        awsConfiguration.getLandingZones()) {
      if (landingZoneConfigurationIter.getName().equals(awsConfiguration.getDefaultLandingZone())) {
        landingZoneConfiguration = landingZoneConfigurationIter;
        break;
      }
    }

    assertNotNull(landingZoneConfiguration);

    AwsCloudContext context =
        AwsCloudContext.fromConfiguration(
            landingZoneConfiguration, awsConfiguration.getGoogleJwtAudience());
    validateContext(context);
  }

  @Test
  void basicSerDes() {
    AwsCloudContext testContext = buildTestContext();
    String outJson = testContext.serialize();
    System.out.println(outJson);

    AwsCloudContext compareContext = AwsCloudContext.deserialize(outJson);
    validateContext(compareContext);
  }

  @Test
  void formatV1() {
    String goodJson =
        "{\"version\":257,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    AwsCloudContext compareContext = AwsCloudContext.deserialize(goodJson);
    validateContext(compareContext);
  }

  @Test
  void badVersion() {
    String badJson =
        "{\"version\":0,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badBucketVersion() {
    String badJson =
        "{\"version\":257,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":0,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badFormat() {
    String badJson = "Junk";
    assertThrows(SerializationException.class, () -> AwsCloudContext.deserialize(badJson));
  }
}
