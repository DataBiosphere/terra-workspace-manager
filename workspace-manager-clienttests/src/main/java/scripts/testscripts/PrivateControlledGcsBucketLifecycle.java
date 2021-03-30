package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceCommonFields.AccessScopeEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
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
import bio.terra.workspace.model.JobReport.StatusEnum;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
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

public class PrivateControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {
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

  private TestUserSpecification privateResourceWriter;
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
    this.privateResourceWriter = testUsers.get(1);
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

    // Create a private bucket
    CreatedControlledGcsBucket bucket = createPrivateBucketAttempt(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource from WSM
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcsBucketAttributes gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertThat(gotBucket.getBucketName(), equalTo(bucket.getGcpBucket().getBucketName()));
    assertThat(gotBucket.getBucketName(), equalTo(bucketName));

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    Storage privateUserStorageClient =
        ClientTestUtils.getGcpStorageClient(privateResourceWriter, projectId);
    Storage workspaceReaderStorageClient =
        ClientTestUtils.getGcpStorageClient(workspaceReader, projectId);

    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(privateResourceWriter.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);
    logger.info(
        "Added {} as a writer to workspace {}", privateResourceWriter.userEmail, getWorkspaceId());

    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(workspaceReader.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    logger.info(
        "Added {} as a reader to workspace {}", workspaceReader.userEmail, getWorkspaceId());

    // TODO: expecting clients to do this feels bad. This should happen inside WSM.
    logger.info("Waiting 10s for permissions to propagate");
    Thread.sleep(10000);

    // Owner cannot write object to bucket
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    try {
      ownerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
      throw new IllegalStateException("Workspace owner was able to write to private bucket");
    } catch (StorageException storageException) {
      assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      logger.info("Workspace owner cannot write to private resource");
    }

    // Private resource user can write object to bucket
    Blob createdBlob = privateUserStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Private resource user can write {} to private resource", blobInfo.getName());

    // Private user can read their bucket
    Blob retrievedBlob = privateUserStorageClient.get(blobId);
    assertThat(retrievedBlob, equalTo(createdBlob));
    logger.info("Private resource user can read {} from bucket", retrievedBlob.getName());

    // Owner cannot read the bucket contents
    try {
      ownerStorageClient.get(blobId);
      throw new IllegalStateException("Workspace owner was able to read private bucket");
    } catch (StorageException storageException) {
      assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      logger.info("Workspace owner cannot read from private bucket");
    }

    // Workspace reader also cannot read private bucket contents
    try {
      workspaceReaderStorageClient.get(blobId);
      throw new IllegalStateException("Workspace reader was able to read private bucket");
    } catch (StorageException storageException) {
      assertThat(storageException.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      logger.info("Workspace reader cannot read from private bucket");
    }

    // Private resource user can delete the blob they created earlier.
    privateUserStorageClient.delete(blobId);
    logger.info("Private resource user successfully deleted blob {}", blobId.getName());

    // Private resource user cannot delete bucket, as they are not an editor
    ControlledGcpResourceApi privateResourceUserWsmClient =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceWriter, server);
    var resourceUserDeleteResult = deleteBucketAttempt(privateResourceUserWsmClient, resourceId);
    logger.info(
        "For resource user, delete bucket status is {}",
        resourceUserDeleteResult.getJobReport().getStatus().toString());
    assertThat(resourceUserDeleteResult.getJobReport().getStatus(), equalTo(StatusEnum.FAILED));

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

    bucketName = null;

    // Delete the cloud context. This is not required. Just some exercise for deleteCloudContext
    logger.info("Deleting the cloud context");
    workspaceApi.deleteCloudContext(getWorkspaceId(), CloudPlatform.GCP);
  }

  private CreatedControlledGcsBucket createPrivateBucketAttempt(
      ControlledGcpResourceApi resourceApi) throws Exception {
    String jobId = UUID.randomUUID().toString();
    var creationParameters =
        new GcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var privateUser = new PrivateResourceIamRoles();
    privateUser.add(ControlledResourceIamRole.WRITER);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScopeEnum.PRIVATE_ACCESS)
            .privateResourceUser(
                new PrivateResourceUser()
                    .userName(privateResourceWriter.userEmail)
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
