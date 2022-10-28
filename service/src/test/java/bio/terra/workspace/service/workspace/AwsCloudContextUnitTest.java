package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.arn.Arn;
import com.amazonaws.regions.Regions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
public class AwsCloudContextUnitTest {

  static final String ACCOUNT_ID = "123456789012";
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

  private void validateContext(AwsCloudContext compareContext) {
    assertEquals(ACCOUNT_ID, compareContext.getAccountNumber());
    assertEquals(ROLE_ARN_SERVICE, compareContext.getServiceRoleArn());
    assertEquals(ROLE_ARN_USER, compareContext.getUserRoleArn());
    assertEquals(BUCKET_NAME_US_EAST, compareContext.getBucketNameForRegion(Regions.US_EAST_1));
    assertEquals(BUCKET_NAME_US_WEST, compareContext.getBucketNameForRegion(Regions.US_WEST_1));
    assertEquals(null, compareContext.getBucketNameForRegion(Regions.EU_CENTRAL_1));
  }

  @Test
  void basicSerDes() {

    AwsCloudContext testContext =
        AwsCloudContext.builder()
            .accountNumber(ACCOUNT_ID)
            .serviceRoleArn(ROLE_ARN_SERVICE)
            .userRoleArn(ROLE_ARN_USER)
            .addBucket(Regions.US_EAST_1, BUCKET_NAME_US_EAST)
            .addBucket(Regions.US_WEST_1, BUCKET_NAME_US_WEST)
            .build();

    String outJson = testContext.serialize();
    System.out.println(outJson);

    AwsCloudContext compareContext = AwsCloudContext.deserialize(outJson);
    validateContext(compareContext);
  }

  @Test
  void formatV1() {
    String goodJson =
        "{\"version\":257,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"},{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"}]}\n";
    AwsCloudContext compareContext = AwsCloudContext.deserialize(goodJson);
    validateContext(compareContext);
  }

  @Test
  void badVersion() {
    String badJson =
        "{\"version\":0,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":257,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"},{\"version\":257,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badBucketVersion() {
    String badJson =
        "{\"version\":257,\"accountNumber\":\"123456789012\",\"serviceRoleArn\":\"arn:aws:iam::123456789012:ServiceRole\",\"userRoleArn\":\"arn:aws:iam::123456789012:UserRole\",\"bucketList\":[{\"version\":0,\"regionName\":\"us-west-1\",\"bucketName\":\"west-region-bucket\"},{\"version\":0,\"regionName\":\"us-east-1\",\"bucketName\":\"east-region-bucket\"}]}\n";
    assertThrows(
        InvalidSerializedVersionException.class, () -> AwsCloudContext.deserialize(badJson));
  }

  @Test
  void badFormat() {
    String badJson = "Junk";
    assertThrows(SerializationException.class, () -> AwsCloudContext.deserialize(badJson));
  }
}
