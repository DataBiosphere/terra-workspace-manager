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
  static final String LZ_NAME = "test-lz";
  static final String ACCOUNT_ID = "123456789012";
  static final String GOOGLE_JWT_AUDIENCE = "test_jwt_audience";
  static final Arn ROLE_ARN_SERVICE = getRoleArn(ACCOUNT_ID, "ServiceRole");
  static final Arn ROLE_ARN_USER = getRoleArn(ACCOUNT_ID, "UserRole");
  static final Arn NOTEBOOK_LIFECYCLE_CONFIG_ARN =
      getLifecycleConfigArn(ACCOUNT_ID, "terra-nb-lifecycle-config-default");
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

  private static Arn getLifecycleConfigArn(String accountId, String configName) {
    return Arn.builder()
        .withPartition("aws")
        .withService("sagemaker")
        .withRegion("us-east-1")
        .withAccountId(accountId)
        .withResource(String.format("notebook-instance-lifecycle-config/%s", configName))
        .build();
  }

  private AwsCloudContext buildTestContext(boolean includeNotebookLifecycleConfig) {
    return AwsCloudContext.builder()
        .landingZoneName(LZ_NAME)
        .accountNumber(ACCOUNT_ID)
        .serviceRoleArn(ROLE_ARN_SERVICE)
        .serviceRoleAudience(GOOGLE_JWT_AUDIENCE)
        .userRoleArn(ROLE_ARN_USER)
        .notebookLifecycleConfigArn(
            includeNotebookLifecycleConfig ? NOTEBOOK_LIFECYCLE_CONFIG_ARN : null)
        .addBucket(Regions.US_EAST_1, BUCKET_NAME_US_EAST)
        .addBucket(Regions.US_WEST_1, BUCKET_NAME_US_WEST)
        .build();
  }

  private AwsCloudContext buildTestContext() {
    return buildTestContext(true);
  }

  private void validateContext(
      AwsCloudContext compareContext, boolean expectNotebookLifecycleConfig) {
    assertEquals(LZ_NAME, compareContext.getLandingZoneName());
    assertEquals(ACCOUNT_ID, compareContext.getAccountNumber());
    assertEquals(ROLE_ARN_SERVICE, compareContext.getServiceRoleArn());
    assertEquals(GOOGLE_JWT_AUDIENCE, compareContext.getServiceRoleAudience());
    assertEquals(ROLE_ARN_USER, compareContext.getUserRoleArn());
    assertEquals(
        expectNotebookLifecycleConfig ? NOTEBOOK_LIFECYCLE_CONFIG_ARN : null,
        compareContext.getNotebookLifecycleConfigArn());
    assertEquals(BUCKET_NAME_US_EAST, compareContext.getBucketNameForRegion(Regions.US_EAST_1));
    assertEquals(BUCKET_NAME_US_WEST, compareContext.getBucketNameForRegion(Regions.US_WEST_1));
    assertEquals(null, compareContext.getBucketNameForRegion(Regions.EU_CENTRAL_1));
  }

  private void validateContext(AwsCloudContext compareContext) {
    validateContext(compareContext, true);
  }

  @Test
  void basic() {
    AwsCloudContext testContext = buildTestContext();
    validateContext(testContext);
  }

  @Test
  void basicNoNotebookLifecycleConfig() {
    AwsCloudContext testContext = buildTestContext(false);
    validateContext(testContext, false);
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
        "{\"version\":257,\"landingZoneName\":\"test-lz\",\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"notebookLifecycleConfigArn\":\"arn:aws:sagemaker:us-east-1:123456789012:notebook-instance-lifecycle-config/terra-nb-lifecycle-config-default\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}";
    AwsCloudContext compareContext = AwsCloudContext.deserialize(goodJson);
    validateContext(compareContext);
  }

  @Test
  void formatV1NoNotebookLifecycleConfig() {
    String goodJson =
        "{\"version\":257,\"landingZoneName\":\"test-lz\",\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    AwsCloudContext compareContext = AwsCloudContext.deserialize(goodJson);
    validateContext(compareContext, false);
  }

  @Test
  void badVersion() {
    String badJson =
        "{\"version\":0,\"landingZoneName\":\"test-lz\",\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badBucketVersion() {
    String badJson =
        "{\"version\":257,\"landingZoneName\":\"test-lz\",\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"serviceRoleAudience\":\"test_jwt_audience\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":0,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"},{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badFormat() {
    String badJson = "Junk";
    assertThrows(SerializationException.class, () -> AwsCloudContext.deserialize(badJson));
  }
}
