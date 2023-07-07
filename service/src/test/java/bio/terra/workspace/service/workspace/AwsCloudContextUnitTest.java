package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.ACCOUNT_ID;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_USER_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_WSM_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_KMS_KEY_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_STORAGE_BUCKET_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_REGION;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.ENVIRONMENT_ALIAS;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.MAJOR_VERSION;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.ORGANIZATION_ID;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.TENANT_ALIAS;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.V1_VERSION;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.SPEND_PROFILE_ID;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.makeDbCloudContext;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.exceptions.InvalidCloudContextStateException;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.regions.Region;

public class AwsCloudContextUnitTest extends BaseAwsUnitTest {

  @Autowired private AwsCloudContextService awsCloudContextService;
  @Mock private S3EnvironmentDiscovery mockEnvironmentDiscovery;

  private final Metadata metadata =
      Metadata.builder()
          .tenantAlias(TENANT_ALIAS)
          .organizationId(ORGANIZATION_ID)
          .environmentAlias(ENVIRONMENT_ALIAS)
          .accountId(ACCOUNT_ID)
          .region(Region.of(AWS_REGION))
          .majorVersion(MAJOR_VERSION)
          .tagMap(Map.of("tagKey", "tagValue"))
          .build();
  private final LandingZone landingZone =
      LandingZone.builder()
          .metadata(metadata)
          .storageBucket(Arn.fromString(AWS_LANDING_ZONE_STORAGE_BUCKET_ARN), "bucket")
          .kmsKey(Arn.fromString(AWS_LANDING_ZONE_KMS_KEY_ARN), UUID.randomUUID())
          .addNotebookLifecycleConfiguration(
              Arn.fromString(AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN), "lifecycleConfig")
          .build();
  private final Environment environment =
      Environment.builder()
          .metadata(metadata)
          .workspaceManagerRoleArn(Arn.fromString(AWS_ENVIRONMENT_WSM_ROLE_ARN))
          .userRoleArn(Arn.fromString(AWS_ENVIRONMENT_USER_ROLE_ARN))
          .notebookRoleArn(Arn.fromString(AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN))
          .addLandingZone(landingZone.getMetadata().getRegion(), landingZone)
          .build();
  private final AwsCloudContext awsCloudContext =
      AwsCloudContextService.createCloudContext("flightId", SPEND_PROFILE_ID, environment);

  @Test
  void serdesTest() {
    // Case 1: successful V2 deserialization
    String v1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\" }",
            V1_VERSION,
            MAJOR_VERSION,
            ORGANIZATION_ID,
            ACCOUNT_ID,
            TENANT_ALIAS,
            ENVIRONMENT_ALIAS);

    DbCloudContext dbCloudContext = makeDbCloudContext(CloudPlatform.AWS, v1Json);
    AwsCloudContext goodV1 = AwsCloudContext.deserialize(dbCloudContext);
    assertEquals(goodV1.getMajorVersion(), MAJOR_VERSION);
    assertEquals(goodV1.getOrganizationId(), ORGANIZATION_ID);
    assertEquals(goodV1.getAccountId(), ACCOUNT_ID);
    assertEquals(goodV1.getTenantAlias(), TENANT_ALIAS);
    assertEquals(goodV1.getEnvironmentAlias(), ENVIRONMENT_ALIAS);

    // Case 2: bad V2 format
    String badV1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\" }",
            V1_VERSION + 1,
            MAJOR_VERSION,
            ORGANIZATION_ID,
            ACCOUNT_ID,
            TENANT_ALIAS,
            ENVIRONMENT_ALIAS);
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize(makeDbCloudContext(CloudPlatform.AWS, badV1Json)),
        "Bad V1 JSON should throw");

    // Case 3: incomplete V2
    String incompleteV2Json =
        String.format("{\"version\": 2, \"organizationId\": \"%s\"}", ORGANIZATION_ID);
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize(makeDbCloudContext(CloudPlatform.AWS, incompleteV2Json)),
        "Incomplete V1 JSON should throw");

    // Case 4: junk input
    String junkJson = "{\"foo\": 15, \"bar\": \"xyzzy\"}";
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize(makeDbCloudContext(CloudPlatform.AWS, junkJson)),
        "Junk JSON should throw");
  }

  @Test
  void discoverEnvironmentTest() throws Exception {
    try (MockedStatic<AwsUtils> mockAwsUtils = mockStatic(AwsUtils.class)) {
      when(mockEnvironmentDiscovery.discoverEnvironment()).thenReturn(environment);
      mockAwsUtils
          .when(() -> AwsUtils.createEnvironmentDiscovery(any()))
          .thenReturn(null)
          .thenReturn(mockEnvironmentDiscovery) /* success */;

      // error (initialization unsuccessful)
      Exception ex =
          assertThrows(
              InvalidApplicationConfigException.class,
              () -> awsCloudContextService.discoverEnvironment());
      assertTrue(ex.getMessage().contains("AWS environmentDiscovery not initialized"));

      // success
      Environment fetchedEnvironment = awsCloudContextService.discoverEnvironment();
      assertNotNull(fetchedEnvironment);

      mockAwsUtils.verify(() -> AwsUtils.createEnvironmentDiscovery(any()), times(2));

      // AwsUtils.createEnvironmentDiscovery is not called another time if already initialized
      awsCloudContextService.discoverEnvironment();
      mockAwsUtils.verify(() -> AwsUtils.createEnvironmentDiscovery(any()), times(2));
    }
  }

  @Test
  void createCloudContextTest() {
    AwsCloudContext createdAwsCloudContext =
        AwsCloudContextService.createCloudContext("flightId", SPEND_PROFILE_ID, environment);
    assertNotNull(createdAwsCloudContext);

    AwsCloudContextFields contextFields = createdAwsCloudContext.getContextFields();
    assertNotNull(contextFields);
    assertEquals(metadata.getMajorVersion(), contextFields.getMajorVersion());
    assertEquals(metadata.getOrganizationId(), contextFields.getOrganizationId());
    assertEquals(metadata.getAccountId(), contextFields.getAccountId());
    assertEquals(metadata.getTenantAlias(), contextFields.getTenantAlias());
    assertEquals(metadata.getEnvironmentAlias(), contextFields.getEnvironmentAlias());

    CloudContextCommonFields commonFields = createdAwsCloudContext.getCommonFields();
    assertNotNull(commonFields);
    assertEquals(SPEND_PROFILE_ID, commonFields.spendProfileId());
    assertEquals(WsmResourceState.CREATING, commonFields.state());
    assertEquals("flightId", commonFields.flightId());
    assertNull(commonFields.error());
  }

  @Test
  void getLandingZoneTest() {
    // success
    Optional<LandingZone> fetchedLandingZone =
        AwsCloudContextService.getLandingZone(
            environment, awsCloudContext, landingZone.getMetadata().getRegion());
    assertTrue(fetchedLandingZone.isPresent(), "landing zone expected for valid region");

    // failure - no landing zone for region
    fetchedLandingZone =
        AwsCloudContextService.getLandingZone(environment, awsCloudContext, Region.of("cloud"));
    assertFalse(fetchedLandingZone.isPresent(), "landing zone not expected for invalid region");
  }

  @Test
  void verifyCloudContextTest() {
    // success
    assertDoesNotThrow(() -> awsCloudContext.verifyCloudContext(environment));

    // error - bad cloud context
    assertThrows(
        InvalidCloudContextStateException.class,
        () -> (new AwsCloudContext(null, null)).verifyCloudContext(environment));
  }

  @Test
  void verifyCloudContextFieldsTest() {
    // success
    assertDoesNotThrow(
        () -> awsCloudContext.getContextFields().verifyCloudContextFields(environment));

    // error - cloud context mismatch
    assertThrows(
        StaleConfigurationException.class,
        () ->
            (new AwsCloudContextFields("a", "b", "c", "d", "e"))
                .verifyCloudContextFields(environment));
  }
}
