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
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRule;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ManagedBy;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);

  private static final GcpGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .type(
                      GcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  private static final GcpGcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .storageClass(GcpGcsBucketDefaultStorageClass.NEARLINE)
                  .type(GcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .createdBefore(OffsetDateTime.parse("2007-01-03T00:00:00.00Z"))
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.STANDARD));

  // list must not be immutable if deserialization is to work
  static final List<GcpGcsBucketLifecycleRule> LIFECYCLE_RULES =
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
    ApiException createBucketFails =
        assertThrows(ApiException.class, () -> createBucketAttempt(resourceApi));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, createBucketFails.getCode());
    logger.info("Failed to create bucket, as expected");

    // Create the cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create the bucket - should work this time
    CreatedControlledGcpGcsBucket bucket = createBucketAttempt(resourceApi);
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcpGcsBucketResource gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertEquals(
        bucket.getGcpBucket().getAttributes().getBucketName(), gotBucket.getAttributes().getBucketName());
    assertEquals(bucketName, gotBucket.getAttributes().getBucketName());

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);

    // Owner can write object to bucket
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    Blob createdFile = ownerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Wrote blob {} to bucket", createdFile.getBlobId());

    // Owner can read the object they created
    Blob retrievedFile = ownerStorageClient.get(blobId);
    assertEquals(createdFile.getBlobId(), retrievedFile.getBlobId());
    logger.info("Read existing blob {} from bucket as owner", retrievedFile.getBlobId());

    // Second user has not yet been added to the workspace, so calls will be rejected.
    Storage readerStorageClient = ClientTestUtils.getGcpStorageClient(reader, projectId);

    // Second user cannot read the object yet.
    StorageException userCannotRead =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.get(blobId),
            "User accessed a controlled workspace bucket without being a workspace member");
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, userCannotRead.getCode());
    logger.info(
        "User {} outside of workspace could not access bucket as expected", reader.userEmail);

    // Owner can add second user as a reader to the workspace
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(reader.userEmail), getWorkspaceId(), IamRole.READER);
    logger.info("Added {} as a reader to workspace {}", reader.userEmail, getWorkspaceId());

    // TODO(PF-643): this should happen inside WSM.
    logger.info("Waiting 15s for permissions to propagate");
    TimeUnit.SECONDS.sleep(15);

    // Second user can now read the blob
    Blob readerRetrievedFile = readerStorageClient.get(blobId);
    assertEquals(createdFile.getBlobId(), readerRetrievedFile.getBlobId());
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
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, readerCannotWrite.getCode());
    logger.info("Failed to write new blob {} as reader as expected", readerBlobId.getName());

    // Reader cannot delete the blob the owner created.
    StorageException readerCannotDeleteBlob =
        assertThrows(
            StorageException.class,
            () -> readerStorageClient.delete(blobId),
            "Workspace reader was able to delete bucket contents!");
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, readerCannotDeleteBlob.getCode());
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
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, readerCannotDeleteBucket.getCode());
    logger.info("Failed to delete bucket {} directly as reader as expected", bucketName);

    // Owner also cannot delete the bucket directly
    StorageException ownerCannotDeleteBucket =
        assertThrows(
            StorageException.class,
            () -> ownerStorageClient.get(bucketName).delete(),
            "Workspace owner was able to delete a bucket directly!");
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, ownerCannotDeleteBucket.getCode());
    logger.info("Failed to delete bucket {} directly as owner as expected", bucketName);

    // Owner can delete the bucket through WSM
    ResourceMaker.deleteControlledGcsBucket(resourceId, getWorkspaceId(), resourceApi);

    // verify the bucket was deleted from WSM metadata
    ApiException bucketNotFound =
        assertThrows(
            ApiException.class,
            () -> resourceApi.getBucket(getWorkspaceId(), resourceId),
            "Incorrectly found a deleted bucket!");
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, bucketNotFound.getCode());

    // also verify it was deleted from GCP
    Bucket maybeBucket = ownerStorageClient.get(bucketName);
    assertNull(maybeBucket);

    bucketName = null;

    // Delete the cloud context. This is not required. Just some exercise for deleteCloudContext
    CloudContextMaker.deleteGcpCloudContext(getWorkspaceId(), workspaceApi);
  }

  private CreatedControlledGcpGcsBucket createBucketAttempt(ControlledGcpResourceApi resourceApi)
      throws Exception {
    var creationParameters =
        new GcpGcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.SHARED_ACCESS)
            .managedBy(ManagedBy.USER);

    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);

    logger.info("Attempting to create bucket {} workspace {}", bucketName, getWorkspaceId());
    return resourceApi.createBucket(body, getWorkspaceId());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (bucketName != null) {
      logger.warn("Test failed to cleanup bucket " + bucketName);
    }
  }
}
