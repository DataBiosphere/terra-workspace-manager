package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.CreateControlledGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcsBucketResult;
import bio.terra.workspace.model.GcsBucketAttributes;
import bio.terra.workspace.model.GcsBucketCreationParameters;
import bio.terra.workspace.model.GcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcsBucketLifecycle;
import bio.terra.workspace.model.GcsBucketLifecycleRule;
import bio.terra.workspace.model.GcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

// This test does not use GcpCloudContextTestScriptBase as it also tests the interaction between
// controlled resources and context creation.
public class CreateGetDeleteControlledGcsBucket extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateGetDeleteControlledGcsBucket.class);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;
  private static final long CREATE_CONTEXT_POLL_SECONDS = 10;

  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new GcsBucketLifecycleRule()
          .action(
              new GcsBucketLifecycleRuleAction()
                  .type(
                      GcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new GcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new GcsBucketLifecycleRule()
          .action(
              new GcsBucketLifecycleRuleAction()
                  .storageClass(GcsBucketDefaultStorageClass.NEARLINE)
                  .type(GcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GcsBucketLifecycleRuleCondition()
                  .createdBefore(LocalDate.of(2017, 2, 18))
                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.STANDARD));

  // list must not be immutable if deserialization is to work
  static final List<GcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));

  private static final String BUCKET_LOCATION = "US-CENTRAL1";
  private static final String BUCKET_PREFIX = "wsmtestbucket-";
  private static final String RESOURCE_PREFIX = "wsmtestresource-";
  private static final String GCS_BLOB_NAME = "wsmtestblob-name";
  private static final String GCS_BLOB_CONTENT = "This is the content of a text file.";

  private TestUserSpecification reader;
  private String bucketName;
  private String resourceName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    this.reader = testUsers.get(1);
    String nameSuffix = UUID.randomUUID().toString();
    this.bucketName = BUCKET_PREFIX + nameSuffix;
    this.resourceName = RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // Create a user-shared controlled GCS bucket - should fail due to no cloud context
    CreatedControlledGcsBucket bucket = createBucketAttempt(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.FAILED));
    assertThat(
        bucket.getErrorReport().getStatusCode(), equalTo(HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    // Create the cloud context
    logger.info("Creating cloud context");

    String contextJobId = UUID.randomUUID().toString();
    var createContext =
        new CreateCloudContextRequest()
            .cloudPlatform(CloudPlatform.GCP)
            .jobControl(new JobControl().id(contextJobId));
    CreateCloudContextResult contextResult =
        workspaceApi.createCloudContext(createContext, getWorkspaceId());
    while (ClientTestUtils.jobIsRunning(contextResult.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_CONTEXT_POLL_SECONDS);
      contextResult = workspaceApi.getCreateCloudContextResult(getWorkspaceId(), contextJobId);
    }
    logger.info("Create context status is {}", contextResult.getJobReport().getStatus().toString());
    assertThat(contextResult.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    final String projectId = contextResult.getGcpContext().getProjectId();
    logger.info("Project ID is {}", projectId);

    // Create the bucket - should work this time
    bucket = createBucketAttempt(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcsBucketAttributes gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertThat(gotBucket.getBucketName(), equalTo(bucket.getGcpBucket().getBucketName()));
    assertThat(gotBucket.getBucketName(), equalTo(bucketName));

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);

    // Owner can write object to bucket
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    Blob createdFile = ownerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Wrote blob {} to bucket", createdFile.getBlobId());

    // Owner can read the object they created
    Blob retrievedFile = ownerStorageClient.get(blobId);
    assertThat(retrievedFile.getBlobId(), equalTo(createdFile.getBlobId()));
    logger.info("Read existing blob {} from bucket as owner", retrievedFile.getBlobId());

    // Second user has not yet been added to the workspace, so calls will be rejected.
    Storage readerStorageClient = ClientTestUtils.getGcpStorageClient(reader, projectId);

    // Second user cannot read the object yet.
    StorageException userCannotRead =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.get(blobId),
            "User accessed a controlled workspace bucket without being a workspace member");
    assertThat(userCannotRead.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    logger.info(
        "User {} outside of workspace could not access bucket as expected", reader.userEmail);

    // Owner can add second user as a reader to the workspace
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(reader.userEmail), getWorkspaceId(), IamRole.READER);
    logger.info("Added {} as a reader to workspace {}", reader.userEmail, getWorkspaceId());

    // TODO(PF-643): this should happen inside WSM.
    logger.info("Waiting 15s for permissions to propagate");
    Thread.sleep(15000);

    // Second user can now read the blob
    Blob readerRetrievedFile = readerStorageClient.get(blobId);
    assertThat(readerRetrievedFile.getBlobId(), equalTo(createdFile.getBlobId()));
    logger.info("Read existing blob {} from bucket as reader", retrievedFile.getBlobId());

    // Reader cannot write an object
    BlobId readerBlobId = BlobId.of(bucketName, "fake-gcs-name");
    BlobInfo readerBlobInfo =
        BlobInfo.newBuilder(readerBlobId).setContentType("text/plain").build();
    StorageException readerCannotWrite =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.create(readerBlobInfo, GCS_BLOB_CONTENT.getBytes()),
            "Workspace reader was able to write a file to a bucket!");
    assertThat(readerCannotWrite.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    logger.info("Failed to write new blob {} as reader as expected", readerBlobId.getName());

    // Reader cannot delete the blob the owner created.
    StorageException readerCannotDeleteBlob =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.delete(blobId),
            "Workspace reader was able to delete bucket contents!");
    assertThat(readerCannotDeleteBlob.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    logger.info("Reader failed to delete blob {} as expected", blobId);

    // Owner can delete the blob they created earlier.
    ownerStorageClient.delete(blobId);
    logger.info("Owner successfully deleted blob {}", blobId.getName());

    // Reader cannot delete the bucket directly
    StorageException readerCannotDeleteBucket =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.get(bucketName).delete(),
            "Workspace reader was able to delete a bucket directly!");
    assertThat(readerCannotDeleteBucket.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    logger.info("Failed to delete bucket {} directly as reader as expected", bucketName);

    // TODO(PF-633): Owners and writers can actually delete buckets due to workspace-level roles
    //  included as a temporary workaround. This needs to be removed as we transition onto WSM
    //  controlled resources.

    // // Owner also cannot delete the bucket directly
    // StorageException ownerCannotDeleteBucket =
    //     assertThrows(
    //         StorageException.class,
    //         () -> ownerStorageClient.get(bucketName).delete(),
    //         "Workspace owner was able to delete a bucket directly!");
    // assertThat(ownerCannotDeleteBucket.getCode(),
    // equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
    // logger.info("Failed to delete bucket {} directly as owner as expected", bucketName);

    // Owner can delete the bucket through WSM
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
    logger.info("Delete bucket status is {}", result.getJobReport().getStatus().toString());
    assertThat(result.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));

    // verify the bucket was deleted from WSM metadata
    ApiException bucketNotFound =
        assertThrows(
            ApiException.class,
            () -> resourceApi.getBucket(getWorkspaceId(), resourceId),
            "Incorrectly found a deleted bucket!");
    assertThat(bucketNotFound.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_NOT_FOUND));

    // also verify it was deleted from GCP
    Bucket maybeBucket = ownerStorageClient.get(bucketName);
    assertThat(maybeBucket, Matchers.is(Matchers.nullValue()));

    bucketName = null;

    // Delete the cloud context. This is not required. Just some exercise for deleteCloudContext
    logger.info("Deleting the cloud context");
    workspaceApi.deleteCloudContext(getWorkspaceId(), CloudPlatform.GCP);
  }

  private CreatedControlledGcsBucket createBucketAttempt(ControlledGcpResourceApi resourceApi)
      throws Exception {
    String jobId = UUID.randomUUID().toString();
    var creationParameters =
        new GcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(ControlledResourceCommonFields.AccessScopeEnum.SHARED_ACCESS)
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

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    super.doCleanup(testUsers, workspaceApi);
    if (bucketName != null) {
      logger.warn("Test failed to cleanup bucket " + bucketName);
    }
  }
}
