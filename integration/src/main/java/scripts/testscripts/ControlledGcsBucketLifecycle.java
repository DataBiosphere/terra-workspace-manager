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
import bio.terra.workspace.model.ControlledResourceIamRole;
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
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BucketField;
import com.google.cloud.storage.Storage.BucketGetOption;
import com.google.cloud.storage.StorageClass;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketAccessTester;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);
  // This is a publicly accessible bucket provided by GCP.
  private static final String PUBLIC_GCP_BUCKET_NAME = "gcp-public-data-landsat";

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
        assertThrows(ApiException.class, () -> createBucketAttempt(resourceApi, bucketName));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, createBucketFails.getCode());
    logger.info("Failed to create bucket, as expected");

    // Create the cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    assertNotNull(projectId);
    logger.info("Created project {}", projectId);

    // Create a bucket with a name that's already taken in another project and not accessible to the
    // WSM SA. This should fail, as bucket names are globally unique in GCP.
    ApiException duplicateNameFails =
        assertThrows(
            ApiException.class,
            () -> createBucketAttempt(resourceApi, ClientTestUtils.TEST_BUCKET_NAME));
    assertEquals(HttpStatus.SC_CONFLICT, duplicateNameFails.getCode());
    logger.info("Failed to create bucket with duplicate name, as expected");

    // Create a bucket with a name that's already taken by a publicly accessible bucket. WSM should
    // have get and read access, as the bucket is open to everyone.
    ApiException publicDuplicateNameFails =
        assertThrows(
            ApiException.class, () -> createBucketAttempt(resourceApi, PUBLIC_GCP_BUCKET_NAME));
    assertEquals(HttpStatus.SC_CONFLICT, publicDuplicateNameFails.getCode());
    logger.info("Failed to create bucket with duplicate name of public bucket, as expected");

    // Create the bucket - should work this time
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

    try (GcsBucketAccessTester tester =
        new GcsBucketAccessTester(testUser, bucketName, projectId)) {
      tester.checkAccess(testUser, ControlledResourceIamRole.EDITOR);

      // Second user has not yet been added to the workspace, use null to indicate no role
      tester.checkAccess(reader, null);

      // Owner can add second user as a reader to the workspace
      workspaceApi.grantRole(
          new GrantRoleRequestBody().memberEmail(reader.userEmail),
          getWorkspaceId(),
          IamRole.READER);
      logger.info("Added {} as a reader to workspace {}", reader.userEmail, getWorkspaceId());

      tester.checkAccessWait(reader, ControlledResourceIamRole.READER);
      tester.checkAccess(testUser, ControlledResourceIamRole.EDITOR);
    }

    // Update the bucket
    final GcpGcsBucketResource resource =
        updateBucketAttempt(
            resourceApi,
            resourceId,
            UPDATED_RESOURCE_NAME,
            UPDATED_DESCRIPTION,
            UPDATE_PARAMETERS_1);
    logger.info(
        "Updated resource name to {} and description to {}",
        resource.getMetadata().getName(),
        resource.getMetadata().getDescription());
    assertEquals(UPDATED_RESOURCE_NAME, resource.getMetadata().getName());
    assertEquals(UPDATED_DESCRIPTION, resource.getMetadata().getDescription());
    // However, invalid updates are rejected.
    String invalidName = "!!!invalid_name!!!";
    ApiException invalidUpdateEx =
        assertThrows(
            ApiException.class,
            () ->
                updateBucketAttempt(
                    resourceApi,
                    resourceId,
                    invalidName,
                    /*updatedDescription=*/ null,
                    /*updateParameters=*/ null));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, invalidUpdateEx.getCode());

    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    final Bucket retrievedUpdatedBucket =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    logger.info("Retrieved bucket {}", retrievedUpdatedBucket.toString());
    assertEquals(StorageClass.NEARLINE, retrievedUpdatedBucket.getStorageClass());
    final List<? extends LifecycleRule> lifecycleRules = retrievedUpdatedBucket.getLifecycleRules();
    lifecycleRules.forEach(r -> logger.info("Lifecycle rule: {}", r.toString()));
    assertThat(lifecycleRules, hasSize(1));

    verifyLifecycleRules(lifecycleRules);

    final GcpGcsBucketResource resource2 =
        updateBucketAttempt(resourceApi, resourceId, null, null, UPDATE_PARAMETERS_2);

    final Bucket retrievedUpdatedBucket2 =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket2.getStorageClass());
    assertEquals(UPDATED_RESOURCE_NAME, resource2.getMetadata().getName()); // no change
    assertEquals(UPDATED_DESCRIPTION, resource2.getMetadata().getDescription()); // no change
    verifyLifecycleRules(retrievedUpdatedBucket2.getLifecycleRules()); // no change

    // test without UpdateParameters
    final GcpGcsBucketResource resource3 =
        updateBucketAttempt(resourceApi, resourceId, UPDATED_RESOURCE_NAME_2, null, null);
    final Bucket retrievedUpdatedBucket3 =
        ownerStorageClient.get(
            bucketName, BucketGetOption.fields(BucketField.LIFECYCLE, BucketField.STORAGE_CLASS));
    assertEquals(UPDATED_RESOURCE_NAME_2, resource3.getMetadata().getName());
    assertEquals(UPDATED_DESCRIPTION, resource3.getMetadata().getDescription()); // no change
    assertEquals(StorageClass.COLDLINE, retrievedUpdatedBucket3.getStorageClass()); // no change
    verifyLifecycleRules(retrievedUpdatedBucket3.getLifecycleRules()); // no change

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

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (bucketName != null) {
      logger.warn("Test failed to cleanup bucket " + bucketName);
    }
  }
}
