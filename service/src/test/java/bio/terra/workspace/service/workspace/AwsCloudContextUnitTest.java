package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.makeDbCloudContext;
import static bio.terra.workspace.service.workspace.model.AwsCloudContextFields.AwsCloudContextV1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.junit.jupiter.api.Test;

public class AwsCloudContextUnitTest extends BaseAwsUnitTest {
  private static final long V1_VERSION = AwsCloudContextV1.getVersion();
  private static final String MAJOR_VERSION = "v0.5.8";
  private static final String ORGANIZATION_ID = "o-organization";
  private static final String ACCOUNT_ID = "1245893245";
  private static final String TENANT_ALIAS = "tenant-saas";
  private static final String ENVIRONMENT_ALIAS = "unit-test-env";

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
    final String incompleteV2Json =
        String.format("{\"version\": 2, \"organizationId\": \"%s\"}", V1_VERSION, ORGANIZATION_ID);
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize(makeDbCloudContext(CloudPlatform.AWS, incompleteV2Json)),
        "Incomplete V1 JSON should throw");

    // Case 4: junk input
    final String junkJson = "{\"foo\": 15, \"bar\": \"xyzzy\"}";
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize(makeDbCloudContext(CloudPlatform.AWS, junkJson)),
        "Junk JSON should throw");
  }
}
