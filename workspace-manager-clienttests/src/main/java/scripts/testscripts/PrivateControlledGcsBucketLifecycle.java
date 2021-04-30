package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(PrivateControlledGcsBucketLifecycle.class);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;

  private static final String BUCKET_LOCATION = "US-CENTRAL1";
  private static final String BUCKET_PREFIX = "wsmtestbucket-";
  private static final String RESOURCE_PREFIX = "wsmtestresource-";
  private static final String GCS_BLOB_NAME = "wsmtestblob-name";
  private static final String GCS_BLOB_CONTENT = "This is the content of a text file.";

  private TestUserSpecification privateResourceUser;
  private TestUserSpecification workspaceReader;
  private String bucketName;
  private String resourceName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.privateResourceUser = testUsers.get(1);
    this.workspaceReader = testUsers.get(2);
    String nameSuffix = UUID.randomUUID().toString();
    this.bucketName = BUCKET_PREFIX + nameSuffix;
    this.resourceName = RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    ControlledGcpResourceApi workspaceOwnerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ControlledGcpResourceApi privateUserResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceUser, server);

    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(workspaceReader.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    logger.info(
        "Added {} as a reader to workspace {}", workspaceReader.userEmail, getWorkspaceId());
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(privateResourceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);
    logger.info(
        "Added {} as a writer to workspace {}", privateResourceUser.userEmail, getWorkspaceId());

    // TODO(PF-643): this should happen inside WSM.
    logger.info("Waiting 15s for permissions to propagate");
    Thread.sleep(15000);

    // Create a private bucket, which privateResourceUser assigns to themself.
    CreatedControlledGcpGcsBucket bucket = createPrivateBucket(privateUserResourceApi);
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource from WSM
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcpGcsBucketResource gotBucket = privateUserResourceApi.getBucket(getWorkspaceId(), resourceId);
    assertEquals(
        gotBucket.getAttributes().getBucketName(),
        bucket.getGcpBucket().getAttributes().getBucketName());
    assertEquals(gotBucket.getAttributes().getBucketName(), bucketName);

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    Storage privateUserStorageClient =
        ClientTestUtils.getGcpStorageClient(privateResourceUser, projectId);
    Storage workspaceReaderStorageClient =
        ClientTestUtils.getGcpStorageClient(workspaceReader, projectId);

    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();

    // TODO(PF-633): workspace owners can write to private buckets via "roles/storage.admin".
    // // Owner cannot write object to bucket
    // StorageException ownerCannotWritePrivateBucket =
    //     assertThrows(
    //         StorageException.class,
    //         () -> ownerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes()),
    //         "Workspace owner was able to write to private bucket");
    // assertEquals(ownerCannotWritePrivateBucket.getCode(), HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    // logger.info("Workspace owner cannot write to private resource as expected");

    // Workspace reader cannot write object to bucket
    StorageException readerCannotWriteBucket =
        assertThrows(
            StorageException.class,
            () -> workspaceReaderStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes()),
            "Workspace reader was able to write to private bucket");
    assertEquals(readerCannotWriteBucket.getCode(), HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    logger.info("Workspace reader cannot write to private resource as expected");

    // Private resource user can write object to bucket
    Blob createdBlob = privateUserStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Private resource user can write {} to private resource", blobInfo.getName());

    // Private user can read their bucket
    Blob retrievedBlob = privateUserStorageClient.get(blobId);
    assertEquals(retrievedBlob, createdBlob);
    logger.info("Private resource user can read {} from bucket", retrievedBlob.getName());

    // TODO(PF-633): workspace owners can read from private buckets via "roles/storage.admin" and
    //  "roles/viewer"
    // // Owner cannot read the bucket contents
    // StorageException ownerCannotReadPrivateBucket =
    //     assertThrows(
    //         StorageException.class,
    //         () -> ownerStorageClient.get(blobId),
    //         "Workspace owner was able to read private bucket");
    // assertEquals(ownerCannotReadPrivateBucket.getCode(), HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    // logger.info("Workspace owner cannot read from private bucket as expected");

    // TODO(PF-633): workspace readers can read from private buckets via "roles/viewer".
    // // Workspace reader also cannot read private bucket contents
    // StorageException readerCannotReadPrivateBucket =
    //     assertThrows(
    //         StorageException.class,
    //         () -> workspaceReaderStorageClient.get(blobId),
    //         "Workspace reader was able to read private bucket");
    // assertEquals(readerCannotReadPrivateBucket.getCode(), HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    // logger.info("Workspace reader cannot read from private bucket as expected");

    // Private resource user can delete the blob they created earlier.
    privateUserStorageClient.delete(blobId);
    logger.info("Private resource user successfully deleted blob {}", blobId.getName());

    // Owner can delete the bucket through WSM
    var ownerDeleteResult = deleteBucket(workspaceOwnerResourceApi, resourceId);
    logger.info(
        "For owner, delete bucket status is {}",
        ownerDeleteResult.getJobReport().getStatus().toString());
    assertEquals(ownerDeleteResult.getJobReport().getStatus(), JobReport.StatusEnum.SUCCEEDED);

    // verify the bucket was deleted from WSM metadata
    ApiException bucketIsMissing =
        assertThrows(
            ApiException.class,
            () -> workspaceOwnerResourceApi.getBucket(getWorkspaceId(), resourceId),
            "Incorrectly found a deleted bucket!");
    assertEquals(bucketIsMissing.getCode(), HttpStatusCodes.STATUS_CODE_NOT_FOUND);

    // also verify it was deleted from GCP
    Bucket maybeBucket = ownerStorageClient.get(bucketName);
    assertNull(maybeBucket);
  }

  private CreatedControlledGcpGcsBucket createPrivateBucket(ControlledGcpResourceApi resourceApi)
      throws Exception {
    var creationParameters =
        new GcpGcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcpGcsBucketLifecycle().rules(Collections.emptyList()));

    var privateUser = new PrivateResourceIamRoles();
    privateUser.add(ControlledResourceIamRole.WRITER);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.PRIVATE_ACCESS)
            .privateResourceUser(
                new PrivateResourceUser()
                    .userName(privateResourceUser.userEmail)
                    .privateResourceIamRoles(privateUser))
            .managedBy(ManagedBy.USER);

    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);

    logger.info("Attempting to create bucket {} workspace {}", bucketName, getWorkspaceId());
    return resourceApi.createBucket(body, getWorkspaceId());
  }

  private DeleteControlledGcpGcsBucketResult deleteBucket(
      ControlledGcpResourceApi resourceApi, UUID resourceId) throws Exception {
    String deleteJobId = UUID.randomUUID().toString();
    var deleteRequest =
        new DeleteControlledGcpGcsBucketRequest().jobControl(new JobControl().id(deleteJobId));
    logger.info("Deleting bucket resource id {} jobId {}", resourceId, deleteJobId);
    DeleteControlledGcpGcsBucketResult result =
        resourceApi.deleteBucket(deleteRequest, getWorkspaceId(), resourceId);
    while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(DELETE_BUCKET_POLL_SECONDS);
      result = resourceApi.getDeleteBucketResult(getWorkspaceId(), deleteJobId);
    }
    return result;
  }
}
