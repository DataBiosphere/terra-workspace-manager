package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS;
import static scripts.utils.ClientTestUtils.TEST_FILE_IN_FINE_GRAINED_BUCKET;
import static scripts.utils.ClientTestUtils.TEST_FOLDER_IN_FINE_GRAINED_BUCKET;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketFileResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class ValidateReferencedResources extends DataRepoTestScriptBase {

  // liam.dragonmaw@test.firecloud.org
  private TestUserSpecification secondUser;
  // elijah.thunderlord@test.firecloud.org
  private TestUserSpecification thirdUser;
  private UUID bqResourceId;
  private UUID bqDataTableResourceId;
  private UUID bucketResourceId;
  private UUID fineGrainedBucketResourceId;
  private UUID snapshotResourceId;
  // reference to gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt
  private UUID bucketFileResourceId;
  // reference to gs://terra_wsm_fine_grained_test_bucket/foo/
  private UUID bucketFolderResourceId;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.secondUser = testUsers.get(1);
    this.thirdUser = testUsers.get(2);

    ApiClient apiClient = ClientTestUtils.getClientForTestUser(testUsers.get(0), server);
    ReferencedGcpResourceApi referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);

    String bqReferenceName = RandomStringUtils.random(6, true, false);
    GcpBigQueryDatasetResource bqReference =
        ResourceMaker.makeBigQueryDatasetReference(
            referencedGcpResourceApi, getWorkspaceId(), bqReferenceName);
    bqResourceId = bqReference.getMetadata().getResourceId();

    String bqDataTableReferenceName =
        RandomStringUtils.random(/*count=*/ 1024, /*letters=*/ true, /*numbers=*/ true);
    GcpBigQueryDataTableResource bqDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            referencedGcpResourceApi, getWorkspaceId(), bqDataTableReferenceName);
    bqDataTableResourceId = bqDataTableReference.getMetadata().getResourceId();

    String bucketReferenceName = RandomStringUtils.random(6, true, false);
    GcpGcsBucketResource bucketReference =
        ResourceMaker.makeGcsBucketReference(
            referencedGcpResourceApi, getWorkspaceId(), bucketReferenceName);
    bucketResourceId = bucketReference.getMetadata().getResourceId();
    String snapshotReferenceName = RandomStringUtils.random(6, true, false);
    DataRepoSnapshotResource snapshotReference =
        ResourceMaker.makeDataRepoSnapshotReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            snapshotReferenceName,
            getDataRepoSnapshotId(),
            getDataRepoInstanceName());
    snapshotResourceId = snapshotReference.getMetadata().getResourceId();

    String bucketFileReferenceName =
        RandomStringUtils.random(6, /*letters*/ true, /*numbers=*/ true);
    String fineGrainedBucketReferenceName =
        RandomStringUtils.random(6, /*letters*/ true, /*numbers=*/ true);
    GcpGcsBucketFileResource bucketFileReference =
        ResourceMaker.makeGcsBucketFileReference(
            referencedGcpResourceApi, getWorkspaceId(), bucketFileReferenceName, TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS, TEST_FILE_IN_FINE_GRAINED_BUCKET);
    fineGrainedBucketResourceId = ResourceMaker.makeGcsBucketWithFineGrainedAccessReference(
        referencedGcpResourceApi, getWorkspaceId(),
        fineGrainedBucketReferenceName, /*cloningInstructions=*/null
    ).getMetadata().getResourceId();
    bucketFileResourceId = bucketFileReference.getMetadata().getResourceId();

    String folderReferenceName =
        RandomStringUtils.random(6, /*letters*/ true, /*numbers=*/ true);
    GcpGcsBucketFileResource bucketFolderReference =
        ResourceMaker.makeGcsBucketFileReference(
            referencedGcpResourceApi, getWorkspaceId(), folderReferenceName, TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS, TEST_FOLDER_IN_FINE_GRAINED_BUCKET);
    bucketFolderResourceId = bucketFolderReference.getMetadata().getResourceId();
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ResourceApi ownerApi = new ResourceApi(ClientTestUtils.getClientForTestUser(testUser, server));
    ResourceApi secondUserApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(secondUser, server));
    ResourceApi thirdUserApi = new ResourceApi(
        ClientTestUtils.getClientForTestUser(thirdUser, server));

    // Add second user as workspace reader, though this will not affect permissions on referenced
    // external objects.
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(secondUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(thirdUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // Check that our main test user has access
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketFileResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketFolderResourceId));

    // Check that our secondary test user does not have access
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bucketFileResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bucketFolderResourceId));

    // Third test user have access to the fine-grained access bucket but does not have access to
    // the file within this bucket.
    assertTrue(thirdUserApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));
    assertTrue(thirdUserApi.checkReferenceAccess(getWorkspaceId(), bucketFileResourceId));
    assertFalse(thirdUserApi.checkReferenceAccess(getWorkspaceId(), bucketFolderResourceId));
  }
}
