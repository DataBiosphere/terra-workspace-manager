package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.service.workspace.model.AwsCloudContext.AwsCloudContextV1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("aws-unit")
public class AwsCloudContextUnitTest extends BaseUnitTest {
  private static final long v1Version = AwsCloudContextV1.getVersion();
  private static final String MAJOR_VERSION = "v9";
  private static final String ORGANIZATION_ID = "o-organization-id";
  private static final String ACCOUNT_ID = "012345678910";
  private static final String TENANT_ALIAS = "terra-tenant";
  private static final String ENVIRONMENT_ALIAS = "terra-environment";

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ResourceDao resourceDao;
  @Autowired private AwsCloudContextService awsCloudContextService;

  @Test
  void serdesTest() {
    String v1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\" }",
            v1Version, MAJOR_VERSION, ORGANIZATION_ID, ACCOUNT_ID, TENANT_ALIAS, ENVIRONMENT_ALIAS);

    // Case 1: successful V1 deserialization
    AwsCloudContext goodV1 = AwsCloudContext.deserialize(v1Json);
    assertNotNull(goodV1);
    assertEquals(goodV1.getMajorVersion(), MAJOR_VERSION);
    assertEquals(goodV1.getOrganizationId(), ORGANIZATION_ID);
    assertEquals(goodV1.getAccountId(), ACCOUNT_ID);
    assertEquals(goodV1.getTenantAlias(), TENANT_ALIAS);
    assertEquals(goodV1.getEnvironmentAlias(), ENVIRONMENT_ALIAS);

    // Case 2: bad V1 format
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize("{\"version\": 0}"),
        "Bad V1 JSON should throw");

    // Case 4=3: incomplete V1
    assertThrows(
        InvalidSerializedVersionException.class,
        () ->
            AwsCloudContext.deserialize(
                String.format(
                    "{\"version\": %d, \"organizationId\": \"%s\"}", v1Version, ORGANIZATION_ID)),
        "Incomplete V1 JSON should throw");

    // Case 3: junk input
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize("{\"foo\": 15, \"bar\": \"xyz\"}"),
        "Junk JSON should throw");
  }

  @Test
  public void deleteAwsContext_deletesControlledResourcesInDb() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    var workspace =
        new Workspace(
            workspaceUuid,
            "my-user-facing-id",
            "deleteAwsContextDeletesControlledResources",
            "description",
            new SpendProfileId("spend-profile"),
            Collections.emptyMap(),
            WorkspaceStage.MC_WORKSPACE,
            DEFAULT_USER_EMAIL,
            null);
    workspaceDao.createWorkspace(workspace, /* applicationIds= */ null);
    // Create a cloud context record in the DB
    String projectId = "fake-project-id";

    AwsCloudContext fakeContext =
        new AwsCloudContext(
            projectId,
            "fakeOrganizationId",
            "fakeAccountId",
            "fakeTenantAlias",
            "fakeEnvironmentAlias");
    final String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.AWS, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.AWS, fakeContext.serialize(), flightId);

    // Create a controlled resource in the DB
    ControlledBigQueryDatasetResource bqDataset =
        ControlledResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid)
            .projectId(projectId)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bqDataset);

    // Also create a reference pointing to the same "cloud" resource
    // TODO(TERRA-195) Create a referenced S3 bucket

    // TODO(TERRA-499) set up CRL mocks when CRL us used (mock S3 resource deletion)

    // Delete the AWS context through the service
    workspaceService.deleteAwsCloudContext(workspace, USER_REQUEST);

    // Verify the context and resource have both been deleted from the DB
    // TODO-Dex
    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.AWS).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> resourceDao.getResource(workspaceUuid, bqDataset.getResourceId()));

    // Verify the reference still exists, even though the underlying "cloud" resource was deleted
    // TODO(TERRA-195) Check that reference S3 bucket exists
  }
}
