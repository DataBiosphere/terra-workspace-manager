package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields.AwsCloudContextV1;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.regions.Region;

// Test Utils for AWS (unit & connected) tests
@Component
public class AwsTestUtils {

  public static final long V1_VERSION = AwsCloudContextV1.getVersion();
  public static final String MAJOR_VERSION = "v0.5.8";
  public static final String ORGANIZATION_ID = "o-organization";
  public static final String ACCOUNT_ID = "1245893245";
  public static final String TENANT_ALIAS = "tenant-saas";
  public static final String ENVIRONMENT_ALIAS = "unit-test-env";
  public static final String AWS_REGION = "us-east-1";
  public static final String AWS_ENVIRONMENT_WSM_ROLE_ARN =
      "arn:aws:iam::10000000001:role/WorkspaceManagerRole";
  public static final String AWS_ENVIRONMENT_USER_ROLE_ARN =
      "arn:aws:iam::20000000002:role/UserRole";
  public static final String AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN =
      "arn:aws:iam::30000000003:role/NotebookRole";
  public static final String AWS_LANDING_ZONE_STORAGE_BUCKET_ARN =
      "arn:aws:iam::40000000004:role/StorageBucket";
  public static final String AWS_LANDING_ZONE_KMS_KEY_ARN = "arn:aws:iam::50000000005:role/KmsKey";
  public static final String AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN =
      "arn:aws:iam::60000000006:role/NotebookLifecycleConfig";
  public static final SamUser SAM_USER_AWS_DISABLED =
      new SamUser("noAws@email.com", "noAwsSubjectId", new BearerToken("noAwsToken"));

  public static final Metadata AWS_METADATA =
      Metadata.builder()
          .tenantAlias(TENANT_ALIAS)
          .organizationId(ORGANIZATION_ID)
          .environmentAlias(ENVIRONMENT_ALIAS)
          .accountId(ACCOUNT_ID)
          .region(Region.of(AWS_REGION))
          .majorVersion(MAJOR_VERSION)
          .tagMap(Map.of("tagKey", "tagValue"))
          .build();
  public static final LandingZone AWS_LANDING_ZONE =
      LandingZone.builder()
          .metadata(AWS_METADATA)
          .storageBucket(Arn.fromString(AWS_LANDING_ZONE_STORAGE_BUCKET_ARN), "bucket")
          .kmsKey(Arn.fromString(AWS_LANDING_ZONE_KMS_KEY_ARN), UUID.randomUUID())
          .addNotebookLifecycleConfiguration(
              Arn.fromString(AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN), "lifecycleConfig")
          .build();
  public static final Environment AWS_ENVIRONMENT =
      Environment.builder()
          .metadata(AWS_METADATA)
          .workspaceManagerRoleArn(Arn.fromString(AWS_ENVIRONMENT_WSM_ROLE_ARN))
          .userRoleArn(Arn.fromString(AWS_ENVIRONMENT_USER_ROLE_ARN))
          .notebookRoleArn(Arn.fromString(AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN))
          .addLandingZone(AWS_LANDING_ZONE.getMetadata().getRegion(), AWS_LANDING_ZONE)
          .build();

  public static AwsCloudContext makeAwsCloudContext() {
    return new AwsCloudContext(
        new AwsCloudContextFields(
            MAJOR_VERSION, ORGANIZATION_ID, ACCOUNT_ID, TENANT_ALIAS, ENVIRONMENT_ALIAS),
        new CloudContextCommonFields(
            DEFAULT_SPEND_PROFILE_ID, WsmResourceState.READY, /*flightId=*/ null, /*error=*/ null));
  }

  public static void assertAwsCloudContextFields(
      Metadata envMetadata, AwsCloudContextFields contextFields) {
    assertNotNull(contextFields);
    assertEquals(envMetadata.getMajorVersion(), contextFields.getMajorVersion());
    assertEquals(envMetadata.getOrganizationId(), contextFields.getOrganizationId());
    assertEquals(envMetadata.getAccountId(), contextFields.getAccountId());
    assertEquals(envMetadata.getTenantAlias(), contextFields.getTenantAlias());
    assertEquals(envMetadata.getEnvironmentAlias(), contextFields.getEnvironmentAlias());
  }

  public static void assertCloudContextCommonFields(
      CloudContextCommonFields commonFields,
      SpendProfileId spendProfileId,
      WsmResourceState state,
      String flightId) {
    assertNotNull(commonFields);
    assertEquals(spendProfileId, commonFields.spendProfileId());
    assertEquals(state, commonFields.state());
    assertEquals(flightId, commonFields.flightId());
    assertNull(commonFields.error());
  }
}
