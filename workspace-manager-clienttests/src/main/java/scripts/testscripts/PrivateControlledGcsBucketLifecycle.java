package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceCommonFields.AccessScopeEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcsBucketResult;
import bio.terra.workspace.model.GcsBucketAttributes;
import bio.terra.workspace.model.GcsBucketCreationParameters;
import bio.terra.workspace.model.GcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcsBucketLifecycle;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
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
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.GcpCloudContextTestScriptBase;

public class PrivateControlledGcsBucketLifecycle extends GcpCloudContextTestScriptBase {
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

    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // Create a private bucket
    CreatedControlledGcsBucket bucket = attemptCreatePrivateBucket(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource from WSM
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcsBucketAttributes gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertThat(gotBucket.getBucketName(), equalTo(bucket.getGcpBucket().getBucketName()));
    assertThat(gotBucket.getBucketName(), equalTo(bucketName));

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, getProjectId());
    Storage privateUserStorageClient =
        ClientTestUtils.getGcpStorageClient(privateResourceUser, getProjectId());
    Storage workspaceReaderStorageClient =
        ClientTestUtils.getGcpStorageClient(workspaceReader, getProjectId());

    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(workspaceReader.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    logger.info(
        "Added {} as a reader to workspace {}", workspaceReader.userEmail, getWorkspaceId());
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(privateResourceUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    logger.info(
        "Added {} as a reader to workspace {}", privateResourceUser.userEmail, getWorkspaceId());

    // TODO(PF-643): this should happen inside WSM.
    logger.info("Waiting 15s for permissions to propagate");
    Thread.sleep(15000);

    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();

    // TODO(PF-633): workspace owners can write to private buckets via "roles/storage.admin".
    // // Owner cannot write object to bucket
    // try {
    //   ownerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    //   throw new IllegalStateException("Workspace owner was able to write to private bucket");
    // } catch (StorageException storageException) {
    //   assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    //   logger.info("Workspace owner cannot write to private resource as expected");
    // }

    // Workspace reader cannot write object to bucket
    try {
      workspaceReaderStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
      throw new IllegalStateException("Workspace reader was able to write to private bucket");
    } catch (StorageException storageException) {
      assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      logger.info("Workspace reader cannot write to private resource as expected");
    }

    // Private resource user can write object to bucket
    Blob createdBlob = privateUserStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Private resource user can write {} to private resource", blobInfo.getName());

    // Private user can read their bucket
    Blob retrievedBlob = privateUserStorageClient.get(blobId);
    assertThat(retrievedBlob, equalTo(createdBlob));
    logger.info("Private resource user can read {} from bucket", retrievedBlob.getName());

    // TODO(PF-633): workspace owners can read from private buckets via "roles/storage.admin" and
    //  "roles/viewer"
    // // Owner cannot read the bucket contents
    // try {
    //   ownerStorageClient.get(blobId);
    //   throw new IllegalStateException("Workspace owner was able to read private bucket");
    // } catch (StorageException storageException) {
    //   assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    //   logger.info("Workspace owner cannot read from private bucket as expected");
    // }

    // TODO(PF-633): workspace readers can read from private buckets via "roles/viewer".
    // // Workspace reader also cannot read private bucket contents
    // try {
    //   workspaceReaderStorageClient.get(blobId);
    //   throw new IllegalStateException("Workspace reader was able to read private bucket");
    // } catch (StorageException storageException) {
    //   assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    //   logger.info("Workspace reader cannot read from private bucket as expected");
    // }

    // Private resource user can delete the blob they created earlier.
    privateUserStorageClient.delete(blobId);
    logger.info("Private resource user successfully deleted blob {}", blobId.getName());

    // Private resource user cannot delete bucket, as they are not an editor
    ControlledGcpResourceApi privateResourceUserWsmClient =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceUser, server);
    try {
      deleteBucketAttempt(privateResourceUserWsmClient, resourceId);
      throw new IllegalStateException("non-editor private resource user was able to delete bucket");
    } catch (ApiException apiException) {
      assertThat(apiException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      logger.info("Private resource user cannot to delete bucket");
    }

    // Owner can delete the bucket through WSM
    var ownerDeleteResult = deleteBucketAttempt(resourceApi, resourceId);
    logger.info(
        "For owner, delete bucket status is {}",
        ownerDeleteResult.getJobReport().getStatus().toString());
    assertThat(
        ownerDeleteResult.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));

    // verify the bucket was deleted from WSM metadata
    try {
      resourceApi.getBucket(getWorkspaceId(), resourceId);
      throw new IllegalStateException("Incorrectly found a deleted bucket!");
    } catch (ApiException ex) {
      assertThat(ex.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
    }

    // also verify it was deleted from GCP
    Bucket maybeBucket = ownerStorageClient.get(bucketName);
    assertThat(maybeBucket, Matchers.is(Matchers.nullValue()));
  }

  private CreatedControlledGcsBucket attemptCreatePrivateBucket(
      ControlledGcpResourceApi resourceApi) throws Exception {
    String jobId = UUID.randomUUID().toString();
    var creationParameters =
        new GcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcsBucketLifecycle().rules(Collections.emptyList()));


    var privateUser = new PrivateResourceIamRoles();
    privateUser.add(ControlledResourceIamRole.WRITER);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScopeEnum.PRIVATE_ACCESS)
            .privateResourceUser(
                new PrivateResourceUser()
                    .userName(privateResourceUser.userEmail)
                    .privateResourceIamRoles(privateUser))
            .managedBy(ControlledResourceCommonFields.ManagedByEnum.USER)
            .jobControl(new JobControl().id(jobId));

    var body =
        new CreateControlledGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);

    logger.info(
        "Attempting to create bucket {} jobId {} workspace {}",
        bucketName,
        jobId,
        getWorkspaceId());
    CreatedControlledGcsBucket bucket = resourceApi.createBucket(body, getWorkspaceId());
    while (ClientTestUtils.jobIsRunning(bucket.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_BUCKET_POLL_SECONDS);
      bucket = resourceApi.getCreateBucketResult(getWorkspaceId(), jobId);
    }
    logger.info("Create bucket status is {}", bucket.getJobReport().getStatus().toString());
    return bucket;
  }

  private DeleteControlledGcsBucketResult deleteBucketAttempt(
      ControlledGcpResourceApi resourceApi, UUID resourceId) throws Exception {
    String deleteJobId = UUID.randomUUID().toString();
    var deleteRequest =
        new DeleteControlledGcsBucketRequest().jobControl(new JobControl().id(deleteJobId));
    logger.info("Deleting bucket resource id {} jobId {}", resourceId, deleteJobId);
    DeleteControlledGcsBucketResult result =
        resourceApi.deleteBucket(deleteRequest, getWorkspaceId(), resourceId);
    while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(DELETE_BUCKET_POLL_SECONDS);
      result = resourceApi.getDeleteBucketResult(getWorkspaceId(), deleteJobId);
    }
    return result;
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    super.doCleanup(testUsers, workspaceApi);
    if (bucketName != null) {
      logger.warn("Test failed to cleanup bucket " + bucketName);
    }
  }
}
