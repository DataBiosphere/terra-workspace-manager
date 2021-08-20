package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static scripts.utils.ClientTestUtils.getOrFail;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_CONTENT;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserPrivate;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneResourceResult;
import bio.terra.workspace.model.CloneWorkspaceRequest;
import bio.terra.workspace.model.CloneWorkspaceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.ResourceCloneDetails;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;

public class CloneWorkspace extends DataRepoTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneWorkspace.class);
  private ControlledGcpResourceApi cloningUserResourceApi;
  private CreatedControlledGcpGcsBucket copyDefinitionSourceBucket;
  private CreatedControlledGcpGcsBucket privateSourceBucket;
  private CreatedControlledGcpGcsBucket sharedCopyNothingSourceBucket;
  private CreatedControlledGcpGcsBucket sharedSourceBucket;
  private String nameSuffix;
  private String sharedBucketSourceResourceName;
  private String sourceProjectId;
  private TestUserSpecification sourceOwnerUser;
  private TestUserSpecification cloningUser;
  private UUID destinationWorkspaceId;
  private WorkspaceApi cloningUserWorkspaceApi;

  // Roles to grant user on private resource
  private static final ImmutableList<ControlledResourceIamRole> PRIVATE_ROLES =
      ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    // set up 2 users
    assertThat(testUsers, hasSize(2));
    // user creating the source resource
    sourceOwnerUser = testUsers.get(0);
    // user cloning the bucket resource
    cloningUser = testUsers.get(1);

    // Build source GCP project in main test workspace
    sourceProjectId = CloudContextMaker
        .createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // add cloning user as reader on the workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // give users resource APIs
    final ControlledGcpResourceApi sourceOwnerResourceApi = ClientTestUtils
        .getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // Create a GCS bucket with data
    // create source bucket with COPY_RESOURCE - should clone fine
    nameSuffix = UUID.randomUUID().toString();
    sharedBucketSourceResourceName = RESOURCE_PREFIX + nameSuffix;
    sharedSourceBucket = makeControlledGcsBucketUserShared(sourceOwnerResourceApi, getWorkspaceId(),
        sharedBucketSourceResourceName, CloningInstructionsEnum.RESOURCE);
    addFileToBucket(sharedSourceBucket);

    // create a private GCS bucket, which the non-creating user can't clone
    final PrivateResourceIamRoles privateRoles = new PrivateResourceIamRoles();
    privateRoles.addAll(PRIVATE_ROLES);
    privateSourceBucket = makeControlledGcsBucketUserPrivate(sourceOwnerResourceApi, getWorkspaceId(),
        UUID.randomUUID().toString(), sourceOwnerUser.userEmail, privateRoles, CloningInstructionsEnum.RESOURCE);
    addFileToBucket(privateSourceBucket);

    // create a GCS bucket with data and COPY_NOTHING instruction
    sharedCopyNothingSourceBucket = makeControlledGcsBucketUserShared(sourceOwnerResourceApi, getWorkspaceId(),
        UUID.randomUUID().toString(), CloningInstructionsEnum.NOTHING);
    addFileToBucket(sharedCopyNothingSourceBucket);

    // create a GCS bucket with data and COPY_DEFINITION
    copyDefinitionSourceBucket = makeControlledGcsBucketUserShared(sourceOwnerResourceApi, getWorkspaceId(),
        UUID.randomUUID().toString(), CloningInstructionsEnum.NOTHING);
    addFileToBucket(copyDefinitionSourceBucket);

    // Create a BigQuery Dataset with tables and COPY_RESOURCE
    // Create a BigQuery dataset with tables and COPY_DEFINITION
    // Create a private BQ dataset
    // Create reference to GCS bucket with COPY_REFERENCE
    // create reference to BQ dataset with COPY_NOTHING
    // create reference to Data Repo Snapshot
    // Give the second user read access to the workspace
  }

  @Override
  protected void doUserJourney(TestUserSpecification sourceOwnerUser, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    // As reader user, clone the workspace
    // Get a new workspace API for the reader
    cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
    final CloneWorkspaceRequest cloneWorkspaceRequest = new CloneWorkspaceRequest()
        .displayName("Cloned Workspace")
        .description("A clone of workspace " + getWorkspaceId().toString())
        .spendProfile(getSpendProfileId()) // TODO- use a different one if available
        .location("us-central1");
    CloneWorkspaceResult cloneResult = cloningUserWorkspaceApi.cloneWorkspace(
        cloneWorkspaceRequest,
        getWorkspaceId());

    final String jobId = cloneResult.getJobReport().getId();
    cloneResult = ClientTestUtils.pollWhileRunning(
        cloneResult,
        () -> cloningUserWorkspaceApi.getCloneWorkspaceResult(getWorkspaceId(), jobId),
        CloneWorkspaceResult::getJobReport,
        Duration.ofSeconds(10));
    logger.info("Clone result: {}", cloneResult);
    ClientTestUtils.assertJobSuccess("Clone Workspace", cloneResult.getJobReport(), cloneResult.getErrorReport());
    assertNull(cloneResult.getErrorReport());

    assertThat(cloneResult.getWorkspace().getResources(), hasSize(4));
    assertEquals(getWorkspaceId(), cloneResult.getWorkspace().getSourceWorkspaceId());
    destinationWorkspaceId = cloneResult.getWorkspace().getDestinationWorkspaceId();
    assertNotNull(destinationWorkspaceId);

    // Verify shared GCS bucket succeeds and is populated
    final ResourceCloneDetails sharedBucketCloneDetails = getOrFail(
        cloneResult.getWorkspace().getResources().stream()
        .filter(r -> sharedSourceBucket.getResourceId().equals(r.getSourceResourceId()))
        .findFirst());
    assertEquals(CloningInstructionsEnum.RESOURCE, sharedBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, sharedBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, sharedBucketCloneDetails.getStewardshipType());
    assertNotNull(sharedBucketCloneDetails.getDestinationResourceId());
    assertEquals(CloneResourceResult.SUCCEEDED, sharedBucketCloneDetails.getResult());

    // We need to get the destination bucket name and project ID
    final WorkspaceDescription destinationWorkspace = cloningUserWorkspaceApi.getWorkspace(destinationWorkspaceId);
    final String destinationProjectId = destinationWorkspace.getGcpContext().getProjectId();
    final var clonedSharedBucket = cloningUserResourceApi.getBucket(destinationWorkspaceId, sharedBucketCloneDetails.getDestinationResourceId());
    retrieveBucketFile(clonedSharedBucket.getAttributes().getBucketName(), destinationProjectId);

    // Verify clone of private bucket fails
    final ResourceCloneDetails privateBucketCloneDetails = getOrFail(
        cloneResult.getWorkspace().getResources().stream()
            .filter(r -> privateSourceBucket.getResourceId().equals(r.getSourceResourceId()))
            .findFirst());
    assertEquals(CloningInstructionsEnum.RESOURCE, privateBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, privateBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, privateBucketCloneDetails.getStewardshipType());
    assertNull(privateBucketCloneDetails.getDestinationResourceId());
    assertEquals(CloneResourceResult.FAILED, privateBucketCloneDetails.getResult());

    // Verify COPY_NOTHING bucket was skipped
    final ResourceCloneDetails copyNothingBucketCloneDetails = getOrFail(
        cloneResult.getWorkspace().getResources().stream()
            .filter(r -> sharedCopyNothingSourceBucket.getResourceId().equals(r.getSourceResourceId()))
            .findFirst());
    assertEquals(CloningInstructionsEnum.NOTHING, copyNothingBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, copyNothingBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyNothingBucketCloneDetails.getStewardshipType());
    assertNull(copyNothingBucketCloneDetails.getDestinationResourceId());
    assertEquals(CloneResourceResult.SKIPPED, copyNothingBucketCloneDetails.getResult());

    // verify COPY_DEFINITION bucket exists but is empty
    final ResourceCloneDetails copyDefinitionBucketDetatils = getOrFail(
        cloneResult.getWorkspace().getResources().stream()
            .filter(r -> copyDefinitionSourceBucket.getResourceId().equals(r.getSourceResourceId()))
            .findFirst());
    assertEquals(CloningInstructionsEnum.DEFINITION, copyDefinitionBucketDetatils.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, copyDefinitionBucketDetatils.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyDefinitionBucketDetatils.getStewardshipType());
    assertNull(copyDefinitionBucketDetatils.getDestinationResourceId());
    assertEquals(CloneResourceResult.SUCCEEDED, copyDefinitionBucketDetatils.getResult());
    final var clonedCopyDefinitionBucket = cloningUserResourceApi.getBucket(destinationWorkspaceId,
        copyDefinitionBucketDetatils.getDestinationResourceId());
    assertEmptyBucket(clonedCopyDefinitionBucket.getAttributes().getBucketName(), destinationProjectId);

    // verify COPY_DEFINITION dataset exists but has no tables
    // verify private dataset clone failed

  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Delete the cloned workspace (will delete context and resources)
    cloningUserWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
  }

  private Blob addFileToBucket(CreatedControlledGcpGcsBucket bucket) throws IOException {
    final Storage sourceOwnerStorageClient = ClientTestUtils.getGcpStorageClient(sourceOwnerUser, sourceProjectId);
    final BlobId blobId = BlobId.of(bucket.getGcpBucket().getAttributes().getBucketName(), GCS_BLOB_NAME);
    final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    return sourceOwnerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
  }

  private Blob retrieveBucketFile(String bucketName, String destinationProjectId) throws IOException {
    Storage cloningUserStorageClient = ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);

    final Blob retrievedFile = cloningUserStorageClient.get(blobId);
    assertNotNull(retrievedFile);
    assertEquals(blobId.getName(), retrievedFile.getBlobId().getName());
//    assertEquals(GCS_BLOB_CONTENT.getBytes(), retrievedFile.getContent());
    return retrievedFile;
  }

  private void assertEmptyBucket(String bucketName, String destinationProjectId) throws IOException {
    Storage cloningUserStorageClient = ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);

    assertNull(cloningUserStorageClient.get(blobId));
  }
}
