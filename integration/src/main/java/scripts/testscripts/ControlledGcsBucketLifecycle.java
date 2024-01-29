package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.CommonResourceFieldsUtil.getResourceDefaultProperties;
import static scripts.utils.GcsBucketUtils.BUCKET_LIFECYCLE_RULES;
import static scripts.utils.GcsBucketUtils.BUCKET_LIFECYCLE_RULE_1_CONDITION_AGE;
import static scripts.utils.GcsBucketUtils.BUCKET_LIFECYCLE_RULE_1_CONDITION_LIVE;
import static scripts.utils.GcsBucketUtils.BUCKET_LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS;
import static scripts.utils.GcsBucketUtils.BUCKET_LOCATION;
import static scripts.utils.GcsBucketUtils.BUCKET_PREFIX;
import static scripts.utils.GcsBucketUtils.BUCKET_RESOURCE_PREFIX;
import static scripts.utils.GcsBucketUtils.BUCKET_UPDATE_PARAMETERS_2;
import static scripts.utils.GcsBucketUtils.BUCKET_UPDATE_PARAMETER_1;
import static scripts.utils.GcsBucketUtils.GCS_BLOB_CONTENT;
import static scripts.utils.GcsBucketUtils.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketUtils.UPDATED_BUCKET_RESOURCE_DESCRIPTION;
import static scripts.utils.GcsBucketUtils.UPDATED_BUCKET_RESOURCE_NAME;
import static scripts.utils.GcsBucketUtils.UPDATED_BUCKET_RESOURCE_NAME_2;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketResult;
import bio.terra.workspace.model.ClonedControlledGcpGcsBucket;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsBucketUpdateParameters;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.UpdateControlledGcpGcsBucketRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BucketField;
import com.google.cloud.storage.Storage.BucketGetOption;
import com.google.cloud.storage.StorageClass;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.CommonResourceFieldsUtil;
import scripts.utils.GcpWorkspaceCloneTestScriptBase;
import scripts.utils.GcsBucketAccessTester;
import scripts.utils.GcsBucketUtils;
import scripts.utils.MultiResourcesUtils;

public class ControlledGcsBucketLifecycle extends GcpWorkspaceCloneTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);

  // This is a publicly accessible bucket provided by GCP.
  private static final String PUBLIC_GCP_BUCKET_NAME = "gcp-public-data-landsat";
  private static final int MAX_BUCKET_NAME_LENGTH = 63;

  private String bucketName;
  private String resourceName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    String nameSuffix = UUID.randomUUID().toString();
    this.bucketName = BUCKET_PREFIX + nameSuffix;
    this.resourceName = BUCKET_RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // -- Test Case 1 --
    // Create a bucket with a name that's already taken by a publicly accessible bucket. WSM should
    // have get and read access, as the bucket is open to everyone, but this should still fail.
    // If bucket already exists, create bucket step has logic that checks if existing bucket is in
    // same project (step reran, so not an error) or not (should throw error). This tests that
    // logic.
    ApiException publicDuplicateNameFails =
        assertThrows(
            ApiException.class, () -> createBucketAttempt(resourceApi, PUBLIC_GCP_BUCKET_NAME));
    assertEquals(HttpStatus.SC_CONFLICT, publicDuplicateNameFails.getCode());
    logger.info("Failed to create bucket with duplicate name of public bucket, as expected");

    // -- Test Case 2 --
    // Create the bucket without the cloud name specified. Cloud name will be auto generated.
    CreatedControlledGcpGcsBucket bucketNoCloudName = createBucketAttempt(resourceApi, null);
    GcpGcsBucketResource gotBucketNoCloudName =
        resourceApi.getBucket(getWorkspaceId(), bucketNoCloudName.getResourceId());
    assertEquals(
        bucketNoCloudName.getGcpBucket().getAttributes().getBucketName(),
        gotBucketNoCloudName.getAttributes().getBucketName());
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    String expectedBucketName = resourceName + "-" + projectId;
    expectedBucketName =
        expectedBucketName.length() > MAX_BUCKET_NAME_LENGTH
            ? expectedBucketName.substring(0, MAX_BUCKET_NAME_LENGTH)
            : expectedBucketName;
    expectedBucketName =
        expectedBucketName.endsWith("-")
            ? expectedBucketName.substring(0, expectedBucketName.length() - 1)
            : expectedBucketName;
    assertEquals(expectedBucketName, gotBucketNoCloudName.getAttributes().getBucketName());
    assertEquals(
        getResourceDefaultProperties(), gotBucketNoCloudName.getMetadata().getProperties());

    GcsBucketUtils.deleteControlledGcsBucket(
        bucketNoCloudName.getResourceId(), getWorkspaceId(), resourceApi);

    // -- Test Case 3 --
    // Clone bucket test
    // - create a bucket
    // - fail to create a duplicate named bucket (duplicate test of above, needed here?)
    // - test that reader has access to the bucket
    // - testUser puts files into the bucket
    // - reader clones the bucket to destination workspace
    CreatedControlledGcpGcsBucket bucket = createBucketAttempt(resourceApi, bucketName);
    UUID resourceId = bucket.getResourceId();

    // Try creating another bucket with the same name. This should fail and should not affect the
    // existing resource.
    ApiException duplicateNameFailsAgain =
        assertThrows(ApiException.class, () -> createBucketAttempt(resourceApi, bucketName));
    assertEquals(HttpStatus.SC_CONFLICT, duplicateNameFailsAgain.getCode());
    logger.info("Failed to create bucket with duplicate name again, as expected");
    // Retrieve the bucket resource
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcpGcsBucketResource gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertEquals(
        bucket.getGcpBucket().getAttributes().getBucketName(),
        gotBucket.getAttributes().getBucketName());
    assertEquals(bucketName, gotBucket.getAttributes().getBucketName());

    // Creating the tester will ensure the testUser has complete access to the bucket.
    // We then ensure the reader has read access.
    try (GcsBucketAccessTester tester =
        new GcsBucketAccessTester(testUser, bucketName, getSourceProjectId())) {
      tester.assertAccessWait(getWorkspaceReader(), ControlledResourceIamRole.READER);
    }

    // Populate bucket to test that objects are cloned
    final Storage sourceOwnerStorageClient =
        ClientTestUtils.getGcpStorageClient(testUser, getSourceProjectId());
    final BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);
    final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    final Blob createdFile =
        sourceOwnerStorageClient.create(
            blobInfo, GCS_BLOB_CONTENT.getBytes(StandardCharsets.UTF_8));
    logger.info("Wrote blob {} to bucket", createdFile.getBlobId());
    // Clone the bucket into another workspace
    ControlledGcpResourceApi readerControlledResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(getWorkspaceReader(), server);
    testCloneBucket(bucket.getGcpBucket(), getWorkspaceReader(), readerControlledResourceApi);
    // Delete file after successful clone so that source bucket deletion later is faster
    sourceOwnerStorageClient.delete(blobId);

    // -- Test Case 4 --
    // Update the bucket test
    // - valid update
    // - invalid update
    // - test update results
    // - make sure the actual bucket is updated
    final GcpGcsBucketResource updatedResource =
        updateBucketAttempt(
            resourceApi,
            resourceId,
            UPDATED_BUCKET_RESOURCE_NAME,
            UPDATED_BUCKET_RESOURCE_DESCRIPTION,
            BUCKET_UPDATE_PARAMETER_1);
    logger.info(
        "Updated resource name to {} and description to {}",
        updatedResource.getMetadata().getName(),
        updatedResource.getMetadata().getDescription());
    assertEquals(UPDATED_BUCKET_RESOURCE_NAME, updatedResource.getMetadata().getName());
    assertEquals(
        UPDATED_BUCKET_RESOURCE_DESCRIPTION, updatedResource.getMetadata().getDescription());
    assertEquals(
        CloningInstructionsEnum.DEFINITION, updatedResource.getMetadata().getCloningInstructions());

    // Invalid updates are rejected.
    String invalidName = "!!!invalid_name!!!";
    ApiException invalidUpdateEx =
        assertThrows(
            ApiException.class,
            () ->
                updateBucketAttempt(
                    resourceApi,
                    resourceId,
                    invalidName,
                    /* updatedDescription= */ null,
                    /* updateParameters= */ null));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, invalidUpdateEx.getCode());

    // Make sure the bucket parameters are updated
    Storage ownerStorageClient =
        ClientTestUtils.getGcpStorageClient(testUser, getSourceProjectId());
    final Bucket retrievedUpdatedBucket =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    logger.info("Retrieved bucket {}", retrievedUpdatedBucket.toString());
    assertEquals(StorageClass.NEARLINE, retrievedUpdatedBucket.getStorageClass());
    final List<? extends LifecycleRule> lifecycleRules = retrievedUpdatedBucket.getLifecycleRules();
    lifecycleRules.forEach(r -> logger.info("Lifecycle rule: {}", r.toString()));
    assertThat(lifecycleRules, hasSize(1));

    verifyUpdatedLifecycleRules(lifecycleRules);

    final GcpGcsBucketResource resource2 =
        updateBucketAttempt(resourceApi, resourceId, null, null, BUCKET_UPDATE_PARAMETERS_2);

    final Bucket retrievedUpdatedBucket2 =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket2.getStorageClass());
    assertEquals(UPDATED_BUCKET_RESOURCE_NAME, resource2.getMetadata().getName()); // no change
    assertEquals(
        UPDATED_BUCKET_RESOURCE_DESCRIPTION, resource2.getMetadata().getDescription()); // no change
    verifyUpdatedLifecycleRules(retrievedUpdatedBucket2.getLifecycleRules()); // no change

    // test without UpdateParameters
    final GcpGcsBucketResource resource3 =
        updateBucketAttempt(resourceApi, resourceId, UPDATED_BUCKET_RESOURCE_NAME_2, null, null);
    final Bucket retrievedUpdatedBucket3 =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(UPDATED_BUCKET_RESOURCE_NAME_2, resource3.getMetadata().getName());
    assertEquals(
        UPDATED_BUCKET_RESOURCE_DESCRIPTION, resource3.getMetadata().getDescription()); // no change
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket3.getStorageClass()); // no change
    verifyUpdatedLifecycleRules(retrievedUpdatedBucket3.getLifecycleRules()); // no change

    // -- Test Case 5 --
    // Enumerate the bucket
    ResourceApi readerApi = ClientTestUtils.getResourceClient(getWorkspaceReader(), server);
    ResourceList bucketList =
        readerApi.enumerateResources(
            getWorkspaceId(), 0, 5, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED);
    assertEquals(1, bucketList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_BUCKET, bucketList);

    // -- Test Case 6 --
    // - Owner can delete the bucket through WSM
    // - the bucket is gone in WSM
    // - the bucket is gone in GCP
    GcsBucketUtils.deleteControlledGcsBucket(resourceId, getWorkspaceId(), resourceApi);

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

  private void verifyUpdatedLifecycleRules(List<? extends LifecycleRule> lifecycleRules) {
    final LifecycleRule rule = lifecycleRules.get(0);
    assertEquals(SetStorageClassLifecycleAction.TYPE, rule.getAction().getActionType());
    final SetStorageClassLifecycleAction setStorageClassLifecycleAction =
        (SetStorageClassLifecycleAction) rule.getAction();
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

  private CreatedControlledGcpGcsBucket createBucketAttempt(
      ControlledGcpResourceApi resourceApi, String bucketName) throws Exception {
    var creationParameters =
        new GcpGcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcpGcsBucketLifecycle().rules(BUCKET_LIFECYCLE_RULES));

    var commonParameters =
        CommonResourceFieldsUtil.makeControlledResourceCommonFields(
            resourceName,
            /* privateUser= */ null,
            CloningInstructionsEnum.NOTHING,
            ManagedBy.USER,
            AccessScope.SHARED_ACCESS);

    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);

    logger.info("Attempting to create bucket {} workspace {}", bucketName, getWorkspaceId());
    logger.info(body.toString());
    return resourceApi.createBucket(body, getWorkspaceId());
  }

  private GcpGcsBucketResource updateBucketAttempt(
      ControlledGcpResourceApi resourceApi,
      UUID resourceId,
      @Nullable String updatedResourceName,
      @Nullable String updatedDescription,
      @Nullable GcpGcsBucketUpdateParameters updateParameters)
      throws ApiException {
    var body =
        new UpdateControlledGcpGcsBucketRequestBody()
            .name(updatedResourceName)
            .description(updatedDescription)
            .updateParameters(updateParameters);
    logger.info(
        "Attempting to update bucket {} resource ID {} workspace {}",
        bucketName,
        resourceId,
        getWorkspaceId());
    return resourceApi.updateGcsBucket(body, getWorkspaceId(), resourceId);
  }

  private void testCloneBucket(
      GcpGcsBucketResource sourceBucket,
      TestUserSpecification cloningUser,
      ControlledGcpResourceApi resourceApi)
      throws Exception {
    final String destinationBucketName = "clone-" + UUID.randomUUID();
    // clone the bucket
    final String clonedBucketDescription = "A cloned bucket";
    final CloneControlledGcpGcsBucketRequest cloneRequest =
        new CloneControlledGcpGcsBucketRequest()
            .bucketName(destinationBucketName)
            .destinationWorkspaceId(getDestinationWorkspaceId())
            .name(sourceBucket.getMetadata().getName())
            .description(clonedBucketDescription)
            .location(null) // use same as src
            .cloningInstructions(CloningInstructionsEnum.RESOURCE)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    logger.info(
        "Cloning bucket\n\tname: {}\n\tresource ID: {}\n\tworkspace: {}\n\t"
            + "projectID: {}\ninto destination bucket\n\tname: {}\n\tworkspace: {}\n\tprojectID: {}",
        sourceBucket.getMetadata().getName(),
        sourceBucket.getMetadata().getResourceId(),
        sourceBucket.getMetadata().getWorkspaceId(),
        getSourceProjectId(),
        destinationBucketName,
        getDestinationWorkspaceId(),
        getDestinationProjectId());
    CloneControlledGcpGcsBucketResult cloneResult =
        resourceApi.cloneGcsBucket(
            cloneRequest,
            sourceBucket.getMetadata().getWorkspaceId(),
            sourceBucket.getMetadata().getResourceId());

    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            () ->
                // TODO(PF-1825): Note that the clone job lives in the source workspace, despite
                //  creating a resource in the destination workspace.
                resourceApi.getCloneGcsBucketResult(
                    getWorkspaceId(), cloneRequest.getJobControl().getId()),
            CloneControlledGcpGcsBucketResult::getJobReport,
            Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess(
        "clone bucket", cloneResult.getJobReport(), cloneResult.getErrorReport());

    final ClonedControlledGcpGcsBucket clonedBucket = cloneResult.getBucket();
    assertEquals(getWorkspaceId(), clonedBucket.getSourceWorkspaceId());
    assertEquals(sourceBucket.getMetadata().getResourceId(), clonedBucket.getSourceResourceId());

    final CreatedControlledGcpGcsBucket createdBucket = clonedBucket.getBucket();
    final GcpGcsBucketResource clonedResource = createdBucket.getGcpBucket();

    assertEquals(destinationBucketName, clonedResource.getAttributes().getBucketName());
    final ResourceMetadata clonedResourceMetadata = clonedResource.getMetadata();
    assertEquals(getDestinationWorkspaceId(), clonedResourceMetadata.getWorkspaceId());
    assertEquals(sourceBucket.getMetadata().getName(), clonedResourceMetadata.getName());
    assertEquals(clonedBucketDescription, clonedResourceMetadata.getDescription());
    final ResourceMetadata sourceMetadata = sourceBucket.getMetadata();
    assertEquals(CloningInstructionsEnum.NOTHING, clonedResourceMetadata.getCloningInstructions());
    assertEquals(sourceMetadata.getCloudPlatform(), clonedResourceMetadata.getCloudPlatform());
    assertEquals(ResourceType.GCS_BUCKET, clonedResourceMetadata.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, clonedResourceMetadata.getStewardshipType());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getAccessScope(),
        clonedResourceMetadata.getControlledResourceMetadata().getAccessScope());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getManagedBy(),
        clonedResourceMetadata.getControlledResourceMetadata().getManagedBy());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getPrivateResourceUser(),
        clonedResourceMetadata.getControlledResourceMetadata().getPrivateResourceUser());
    assertEquals(CloudPlatform.GCP, clonedResourceMetadata.getCloudPlatform());
    final Storage destinationProjectStorageClient =
        ClientTestUtils.getGcpStorageClient(cloningUser, getDestinationProjectId());
    final Bucket destinationGcsBucket = destinationProjectStorageClient.get(destinationBucketName);
    // Location, storage class, and lifecycle rules should match values from createBucketAttempt
    assertEquals(StorageClass.STANDARD, destinationGcsBucket.getStorageClass());
    assertEquals(
        BUCKET_LOCATION, destinationGcsBucket.getLocation()); // default since not specified
    assertEquals(2, destinationGcsBucket.getLifecycleRules().size());
    verifyClonedLifecycleRules(destinationGcsBucket);
    assertEquals(CloningInstructionsEnum.RESOURCE, clonedBucket.getEffectiveCloningInstructions());

    // test retrieving file from destination bucket
    Storage cloningUserStorageClient =
        ClientTestUtils.getGcpStorageClient(cloningUser, getDestinationProjectId());
    BlobId blobId = BlobId.of(destinationBucketName, GCS_BLOB_NAME);
    assertNotNull(blobId);

    final Blob retrievedFile = cloningUserStorageClient.get(blobId);
    assertNotNull(retrievedFile);
    assertEquals(blobId.getName(), retrievedFile.getBlobId().getName());
  }

  private void verifyClonedLifecycleRules(Bucket destinationBucket) {
    // We can't rely on the order of the lifecycle rules being maintained
    final LifecycleRule clonedDeleteRule =
        destinationBucket.getLifecycleRules().stream()
            .filter(r -> DeleteLifecycleAction.TYPE.equals(r.getAction().getActionType()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find Delete lifecycle rule."));
    assertEquals(BUCKET_LIFECYCLE_RULE_1_CONDITION_AGE, clonedDeleteRule.getCondition().getAge());
    assertEquals(
        BUCKET_LIFECYCLE_RULE_1_CONDITION_LIVE, clonedDeleteRule.getCondition().getIsLive());
    assertEquals(
        StorageClass.ARCHIVE, clonedDeleteRule.getCondition().getMatchesStorageClass().get(0));
    assertEquals(
        BUCKET_LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS,
        clonedDeleteRule.getCondition().getNumberOfNewerVersions());

    final LifecycleRule setStorageClassRule =
        destinationBucket.getLifecycleRules().stream()
            .filter(r -> SetStorageClassLifecycleAction.TYPE.equals(r.getAction().getActionType()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find SetStorageClass lifecycle rule."));
    final SetStorageClassLifecycleAction setStorageClassLifecycleAction =
        (SetStorageClassLifecycleAction) setStorageClassRule.getAction();
    assertEquals(StorageClass.NEARLINE, setStorageClassLifecycleAction.getStorageClass());
    assertEquals(
        DateTime.parseRfc3339("2007-01-03"), setStorageClassRule.getCondition().getCreatedBefore());
    assertThat(
        setStorageClassRule.getCondition().getMatchesStorageClass(),
        contains(StorageClass.STANDARD));
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
