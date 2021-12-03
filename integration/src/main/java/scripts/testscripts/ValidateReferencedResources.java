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
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class ValidateReferencedResources extends DataRepoTestScriptBase {

  // This user has no acess to any resources.
  private TestUserSpecification noAccessUser;
  // This user has no access to the terra_wsm_fine_grained_test_bucket but have READER access to
  // the foo/monkey_sees_monkey_dos.txt object in that bucket.
  private TestUserSpecification fileReaderUser;
  private UUID bqResourceId;
  private UUID bqDataTableResourceId;
  private UUID bucketResourceId;
  private UUID fineGrainedBucketResourceId;
  private UUID snapshotResourceId;
  // resource id of the reference to
  // gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt
  private UUID bucketTxtFileResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/
  private UUID fooFolderResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/**.txt
  private UUID fooTxtFilesResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/*
  private UUID fooAllFilesResourceId;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.noAccessUser = testUsers.get(1);
    this.fileReaderUser = testUsers.get(2);

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

    // Create bucket file references.

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt. Bella
    // and Elijah has READER access to this file.
    GcpGcsObjectResource bucketFileReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "reference_to_foo_monkey_sees_monkey_dos",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            TEST_FILE_IN_FINE_GRAINED_BUCKET);
    bucketTxtFileResourceId = bucketFileReference.getMetadata().getResourceId();

    // Reference to gs://terra_wsm_fine_grained_test_bucket. Bella and Elijah has READER access.
    fineGrainedBucketResourceId =
        ResourceMaker.makeGcsBucketWithFineGrainedAccessReference(
                referencedGcpResourceApi,
                getWorkspaceId(),
                "bucket_with_fine_grained_access",
                /*cloningInstructions=*/ null)
            .getMetadata()
            .getResourceId();

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/. Only Bella has READER access.
    GcpGcsObjectResource bucketFolderReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "foo_folder",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            TEST_FOLDER_IN_FINE_GRAINED_BUCKET);
    fooFolderResourceId = bucketFolderReference.getMetadata().getResourceId();

    GcpGcsObjectResource fooTxtFileReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "foo_txt_files",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            "foo/**.txt");
    fooTxtFilesResourceId = fooTxtFileReference.getMetadata().getResourceId();

    GcpGcsObjectResource fooAllFilesReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "foo_all_files",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            "foo/*");
    fooAllFilesResourceId = fooAllFilesReference.getMetadata().getResourceId();
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ResourceApi ownerApi = new ResourceApi(ClientTestUtils.getClientForTestUser(testUser, server));
    ResourceApi noAccessUserApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(noAccessUser, server));
    ResourceApi fileReaderApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(fileReaderUser, server));

    // Add noAccessUser and fileReaderUser as workspace reader, though this will not affect permissions on referenced
    // external objects.
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(noAccessUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(fileReaderUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // Check that our main test user has access
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));

    // Check that our secondary test user does not have access
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));

    // Bucket object access
    // Reference to gs://terra_wsm_fine_grained_test_bucket
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketTxtFileResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bucketTxtFileResourceId));
    assertTrue(fileReaderApi.checkReferenceAccess(getWorkspaceId(), bucketTxtFileResourceId));

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/. Only Bella has access.
    // folder.
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fooFolderResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fooFolderResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fooFolderResourceId));

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/**.txt. Only Bella has access.
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fooTxtFilesResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fooTxtFilesResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fooTxtFilesResourceId));

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/*. Only Bella has access.
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fooAllFilesResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fooAllFilesResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fooAllFilesResourceId));
  }
}
