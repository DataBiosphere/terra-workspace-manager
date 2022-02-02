package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ParameterUtils;
import scripts.utils.ResourceMaker;

public class ValidateReferencedResources extends DataRepoTestScriptBase {

  // This user has no access to any resources.
  private TestUserSpecification noAccessUser;
  // This user has no access to the terra_wsm_fine_grained_test_bucket but have READER access to
  // the foo/monkey_sees_monkey_dos.txt object in that bucket.
  private TestUserSpecification fileReaderUser;
  private UUID bqResourceId;
  private GcpBigQueryDataTableAttributes bqTableAttributes;
  private UUID bqDataTableResourceId;
  private GcpGcsBucketAttributes gcsUniformBucketAttributes;
  private UUID bucketResourceId;
  private GcpGcsBucketAttributes gcsFineGrainedBucketAttributes;
  private UUID fineGrainedBucketResourceId;
  private UUID snapshotResourceId;
  // resource id of the reference to
  // gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt
  private GcpGcsObjectAttributes gcsFileAttributes;
  private UUID bucketTxtFileResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/
  private GcpGcsObjectAttributes gcsFolderAttributes;
  private UUID fooFolderResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/**.txt
  private UUID fooTxtFilesResourceId;
  // resource id of reference to gs://terra_wsm_fine_grained_test_bucket/foo/*
  private UUID fooAllFilesResourceId;

  @Override
  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    bqTableAttributes = ParameterUtils.getBigQueryDataTableReference(parameters);
    gcsFineGrainedBucketAttributes = ParameterUtils.getFineGrainedBucketReference(parameters);
    gcsUniformBucketAttributes = ParameterUtils.getUniformBucketReference(parameters);
    gcsFileAttributes = ParameterUtils.getGcsFileReference(parameters);
    gcsFolderAttributes = ParameterUtils.getGcsFolderReference(parameters);
  }

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
    // Use the dataset holding the BQ table parameter.
    GcpBigQueryDatasetAttributes bqDatasetAttributes =
        new GcpBigQueryDatasetAttributes()
            .projectId(bqTableAttributes.getProjectId())
            .datasetId(bqTableAttributes.getDatasetId());
    GcpBigQueryDatasetResource bqReference =
        ResourceMaker.makeBigQueryDatasetReference(
            bqDatasetAttributes, referencedGcpResourceApi, getWorkspaceId(), bqReferenceName);
    bqResourceId = bqReference.getMetadata().getResourceId();

    String bqDataTableReferenceName =
        RandomStringUtils.random(/*count=*/ 1024, /*letters=*/ true, /*numbers=*/ true);
    GcpBigQueryDataTableResource bqDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            bqTableAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            bqDataTableReferenceName);
    bqDataTableResourceId = bqDataTableReference.getMetadata().getResourceId();

    String bucketReferenceName = RandomStringUtils.random(6, true, false);
    GcpGcsBucketResource bucketReference =
        ResourceMaker.makeGcsBucketReference(
            gcsUniformBucketAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            bucketReferenceName,
            CloningInstructionsEnum.NOTHING);
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
            gcsFileAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            "reference_to_foo_monkey_sees_monkey_dos",
            null);
    bucketTxtFileResourceId = bucketFileReference.getMetadata().getResourceId();

    // Reference to gs://terra_wsm_fine_grained_test_bucket. Bella and Elijah has READER access.
    fineGrainedBucketResourceId =
        ResourceMaker.makeGcsBucketReference(
                gcsFineGrainedBucketAttributes,
                referencedGcpResourceApi,
                getWorkspaceId(),
                "bucket_with_fine_grained_access",
                /*cloningInstructions=*/ null)
            .getMetadata()
            .getResourceId();

    // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/. Only Bella has READER access.
    GcpGcsObjectResource bucketFolderReference =
        ResourceMaker.makeGcsObjectReference(
            gcsFolderAttributes, referencedGcpResourceApi, getWorkspaceId(), "foo_folder", null);
    fooFolderResourceId = bucketFolderReference.getMetadata().getResourceId();

    GcpGcsObjectAttributes allTxtAttributes =
        new GcpGcsObjectAttributes()
            .bucketName(gcsFolderAttributes.getBucketName())
            .fileName(gcsFolderAttributes.getFileName() + "**.txt");
    GcpGcsObjectResource fooTxtFileReference =
        ResourceMaker.makeGcsObjectReference(
            allTxtAttributes, referencedGcpResourceApi, getWorkspaceId(), "foo_txt_files", null);
    fooTxtFilesResourceId = fooTxtFileReference.getMetadata().getResourceId();

    GcpGcsObjectAttributes allFilesAttributes =
        new GcpGcsObjectAttributes()
            .bucketName(gcsFolderAttributes.getBucketName())
            .fileName(gcsFolderAttributes.getFileName() + "*");
    GcpGcsObjectResource fooAllFilesReference =
        ResourceMaker.makeGcsObjectReference(
            allFilesAttributes, referencedGcpResourceApi, getWorkspaceId(), "foo_all_files", null);
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

    // Add noAccessUser and fileReaderUser as workspace reader, though this will not affect
    // permissions on referenced
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
    assertFalse(
        noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));

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
