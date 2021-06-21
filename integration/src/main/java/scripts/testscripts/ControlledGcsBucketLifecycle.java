package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_LOCATION;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_CONTENT;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.UPDATED_DESCRIPTION;
import static scripts.utils.GcsBucketTestFixtures.UPDATED_RESOURCE_NAME;
import static scripts.utils.GcsBucketTestFixtures.UPDATED_RESOURCE_NAME_2;
import static scripts.utils.GcsBucketTestFixtures.UPDATE_PARAMETERS_1;
import static scripts.utils.GcsBucketTestFixtures.UPDATE_PARAMETERS_2;

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
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsBucketUpdateParameters;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.UpdateControlledGcpGcsBucketRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BucketField;
import com.google.cloud.storage.Storage.BucketGetOption;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketTestFixtures;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);

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
    Blob readerRetrievedFile = ClientTestUtils
        .getWithRetryOnException(() ->
            readerStorageClient.get(blobId),
            20,
            Duration.ofSeconds(30));
    assertNotNull(readerRetrievedFile);
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

    // Update the bucket
    final GcpGcsBucketResource resource = updateBucketAttempt(resourceApi, resourceId, UPDATED_RESOURCE_NAME, UPDATED_DESCRIPTION,
        UPDATE_PARAMETERS_1);
    logger.info("Updated resource name to {} and description to {}",
        resource.getMetadata().getName(), resource.getMetadata().getDescription());
    assertEquals(UPDATED_RESOURCE_NAME, resource.getMetadata().getName());
    assertEquals(UPDATED_DESCRIPTION, resource.getMetadata().getDescription());
    final Bucket retrievedUpdatedBucket = ownerStorageClient.get(bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    logger.info("Retrieved bucket {}", retrievedUpdatedBucket.toString());
    assertEquals(StorageClass.NEARLINE, retrievedUpdatedBucket.getStorageClass());
    final List<? extends LifecycleRule> lifecycleRules = retrievedUpdatedBucket.getLifecycleRules();
    lifecycleRules
        .forEach(r -> logger.info("Lifecycle rule: {}", r.toString()));
    assertThat(lifecycleRules, hasSize(1));

    verifyLifecycleRules(lifecycleRules);

    final GcpGcsBucketResource resource2 = updateBucketAttempt(resourceApi, resourceId, null, null,
        UPDATE_PARAMETERS_2);

    final Bucket retrievedUpdatedBucket2 = ownerStorageClient.get(bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket2.getStorageClass());
    assertEquals(UPDATED_RESOURCE_NAME, resource2.getMetadata().getName()); // no change
    assertEquals(UPDATED_DESCRIPTION, resource2.getMetadata().getDescription()); // no change
    verifyLifecycleRules(retrievedUpdatedBucket2.getLifecycleRules()); // no change

    // test without UpdateParameters
    final GcpGcsBucketResource resource3 = updateBucketAttempt(resourceApi, resourceId, UPDATED_RESOURCE_NAME_2, null, null);
    final Bucket retrievedUpdatedBucket3 = ownerStorageClient.get(bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(UPDATED_RESOURCE_NAME_2, resource3.getMetadata().getName());
    assertEquals(UPDATED_DESCRIPTION, resource3.getMetadata().getDescription()); // no change
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket3.getStorageClass()); // no change
    verifyLifecycleRules(retrievedUpdatedBucket3.getLifecycleRules()); // no change

    // additional details must be verified with gsutil or in the cloud console, as we don't return them
    logger.info("About to try to delete the bucket with a reader.");
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
    logger.info("Cloud context deleted. User Journey complete.");
  }

  private void verifyLifecycleRules(List<? extends LifecycleRule> lifecycleRules) {
    final LifecycleRule rule = lifecycleRules.get(0);
    assertEquals(SetStorageClassLifecycleAction.TYPE, rule.getAction().getActionType());
    final SetStorageClassLifecycleAction setStorageClassLifecycleAction = (SetStorageClassLifecycleAction) rule.getAction();
    assertEquals(StorageClass.ARCHIVE, setStorageClassLifecycleAction.getStorageClass());
    final LifecycleCondition condition = rule.getCondition();
    assertEquals(30, condition.getAge());
    // The datetime gets simplified to midnight UTC somewhere along the line
    assertEquals(DateTime.parseRfc3339("1981-04-21"), condition.getCreatedBefore());
    assertTrue(condition.getIsLive());
    assertEquals(3, condition.getNumberOfNewerVersions());
    final List<StorageClass> matchesStorageClass = condition.getMatchesStorageClass();
    assertThat(matchesStorageClass, hasSize(1));
    assertEquals(StorageClass.ARCHIVE, matchesStorageClass.get(0));
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

  private GcpGcsBucketResource updateBucketAttempt(ControlledGcpResourceApi resourceApi, UUID resourceId,
      @Nullable String updatedResourceName, @Nullable String updatedDescription, GcpGcsBucketUpdateParameters updateParameters)
    throws ApiException {
    var body = new UpdateControlledGcpGcsBucketRequestBody()
        .name(updatedResourceName)
        .description(updatedDescription)
        .updateParameters(updateParameters);
    logger.info("Attempting to update bucket {} resource ID {} workspace {}", bucketName, resourceId, getWorkspaceId());
    return resourceApi.updateGcsBucket(body, getWorkspaceId(), resourceId);
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
