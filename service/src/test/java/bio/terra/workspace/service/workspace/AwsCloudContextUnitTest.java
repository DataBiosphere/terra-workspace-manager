package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.AwsTestUtils.ACCOUNT_ID;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_ENVIRONMENT;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_LANDING_ZONE;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_METADATA;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_REGION;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_WORKSPACE_SECURITY_GROUPS;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_WORKSPACE_SECURITY_GROUP_ID;
import static bio.terra.workspace.common.utils.AwsTestUtils.ENVIRONMENT_ALIAS;
import static bio.terra.workspace.common.utils.AwsTestUtils.MAJOR_VERSION;
import static bio.terra.workspace.common.utils.AwsTestUtils.ORGANIZATION_ID;
import static bio.terra.workspace.common.utils.AwsTestUtils.TENANT_ALIAS;
import static bio.terra.workspace.common.utils.AwsTestUtils.V1_VERSION;
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
import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.exceptions.InvalidCloudContextStateException;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.regions.Region;

public class AwsCloudContextUnitTest extends BaseAwsSpringBootUnitTest {

  @Autowired private AwsCloudContextService awsCloudContextService;
  @Mock private S3EnvironmentDiscovery mockEnvironmentDiscovery;

  private final AwsCloudContext awsCloudContext =
      AwsCloudContextService.createCloudContext(
          "flightId", DEFAULT_GCP_SPEND_PROFILE_ID, AWS_ENVIRONMENT, AWS_WORKSPACE_SECURITY_GROUPS);

  @Test
  void serdesTest() {
    // Case 1: successful V2 deserialization
    String v1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\", \"applicationSecurityGroups\": {\"%s\": \"%s\"} }",
            V1_VERSION,
            MAJOR_VERSION,
            ORGANIZATION_ID,
            ACCOUNT_ID,
            TENANT_ALIAS,
            ENVIRONMENT_ALIAS,
            AWS_REGION,
            AWS_WORKSPACE_SECURITY_GROUP_ID);

    DbCloudContext dbCloudContext = makeDbCloudContext(CloudPlatform.AWS, v1Json);
    AwsCloudContext goodV1 = AwsCloudContext.deserialize(dbCloudContext);
    assertEquals(goodV1.getMajorVersion(), MAJOR_VERSION);
    assertEquals(goodV1.getOrganizationId(), ORGANIZATION_ID);
    assertEquals(goodV1.getAccountId(), ACCOUNT_ID);
    assertEquals(goodV1.getTenantAlias(), TENANT_ALIAS);
    assertEquals(goodV1.getEnvironmentAlias(), ENVIRONMENT_ALIAS);
    assertEquals(goodV1.getApplicationSecurityGroups(), AWS_WORKSPACE_SECURITY_GROUPS);

    // Case 1.5: successful V2 deserialization with no security groups
    v1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\" }",
            V1_VERSION,
            MAJOR_VERSION,
            ORGANIZATION_ID,
            ACCOUNT_ID,
            TENANT_ALIAS,
            ENVIRONMENT_ALIAS);

    dbCloudContext = makeDbCloudContext(CloudPlatform.AWS, v1Json);
    goodV1 = AwsCloudContext.deserialize(dbCloudContext);
    assertEquals(goodV1.getMajorVersion(), MAJOR_VERSION);
    assertEquals(goodV1.getOrganizationId(), ORGANIZATION_ID);
    assertEquals(goodV1.getAccountId(), ACCOUNT_ID);
    assertEquals(goodV1.getTenantAlias(), TENANT_ALIAS);
    assertEquals(goodV1.getEnvironmentAlias(), ENVIRONMENT_ALIAS);
    assertNull(goodV1.getApplicationSecurityGroups());

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
      when(mockEnvironmentDiscovery.discoverEnvironment()).thenReturn(AWS_ENVIRONMENT);
      mockAwsUtils
          .when(() -> AwsUtils.createEnvironmentDiscovery(any()))
          .thenReturn(null)
          .thenReturn(mockEnvironmentDiscovery) /* success */;

      // error (initialization unsuccessful)
      Exception ex =
          assertThrows(
              InvalidApplicationConfigException.class,
              () -> awsCloudContextService.discoverEnvironment(DEFAULT_USER_EMAIL));
      assertTrue(ex.getMessage().contains("AWS environmentDiscovery not initialized"));

      // success
      Environment fetchedEnvironment =
          awsCloudContextService.discoverEnvironment(DEFAULT_USER_EMAIL);
      assertNotNull(fetchedEnvironment);

      mockAwsUtils.verify(() -> AwsUtils.createEnvironmentDiscovery(any()), times(2));

      // AwsUtils.createEnvironmentDiscovery is not called another time if already initialized
      awsCloudContextService.discoverEnvironment(DEFAULT_USER_EMAIL);
      mockAwsUtils.verify(() -> AwsUtils.createEnvironmentDiscovery(any()), times(2));
    }
  }

  @Test
  void createCloudContextTest() {
    AwsCloudContext createdCloudContext =
        AwsCloudContextService.createCloudContext(
            "flightId", DEFAULT_GCP_SPEND_PROFILE_ID, AWS_ENVIRONMENT, AWS_WORKSPACE_SECURITY_GROUPS);
    assertNotNull(createdCloudContext);
    AwsTestUtils.assertAwsCloudContextFields(AWS_METADATA, createdCloudContext.getContextFields());
    AwsTestUtils.assertCloudContextCommonFields(
        createdCloudContext.getCommonFields(),
            DEFAULT_GCP_SPEND_PROFILE_ID,
        WsmResourceState.CREATING,
        "flightId");
  }

  @Test
  void getLandingZoneTest() {
    // success
    Optional<LandingZone> fetchedLandingZone =
        AwsCloudContextService.getLandingZone(
            AWS_ENVIRONMENT, awsCloudContext, AWS_LANDING_ZONE.getMetadata().getRegion());
    assertTrue(fetchedLandingZone.isPresent(), "landing zone expected for valid region");

    // failure - no landing zone for region
    fetchedLandingZone =
        AwsCloudContextService.getLandingZone(AWS_ENVIRONMENT, awsCloudContext, Region.of("cloud"));
    assertFalse(fetchedLandingZone.isPresent(), "landing zone not expected for invalid region");
  }

  @Test
  void verifyCloudContextTest() {
    // success
    assertDoesNotThrow(() -> awsCloudContext.verifyCloudContext(AWS_ENVIRONMENT));

    // error - bad cloud context
    assertThrows(
        InvalidCloudContextStateException.class,
        () -> (new AwsCloudContext(null, null)).verifyCloudContext(AWS_ENVIRONMENT));
  }

  @Test
  void verifyCloudContextFieldsTest() {
    // success
    assertDoesNotThrow(
        () -> awsCloudContext.getContextFields().verifyCloudContextFields(AWS_ENVIRONMENT));

    // error - cloud context mismatch
    assertThrows(
        StaleConfigurationException.class,
        () ->
            (new AwsCloudContextFields("a", "b", "c", "d", "e", null))
                .verifyCloudContextFields(AWS_ENVIRONMENT));
  }
}
