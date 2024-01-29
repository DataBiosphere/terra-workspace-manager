package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserPrivate;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.CommonResourceFieldsUtil;
import scripts.utils.GcsBucketAccessTester;
import scripts.utils.GcsBucketUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.RetryUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(PrivateControlledGcsBucketLifecycle.class);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;

  private static final String BUCKET_PREFIX = "wsmtestbucket-";
  private static final String RESOURCE_PREFIX = "wsmtestresource-";
  private static final int MAX_BUCKET_NAME_LENGTH = 63;

  private TestUserSpecification privateResourceUser;
  private TestUserSpecification workspaceReader;
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
    this.resourceName = RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    ControlledGcpResourceApi workspaceOwnerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ControlledGcpResourceApi privateUserResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceUser, server);

    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), workspaceReader, IamRole.READER);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), privateResourceUser, IamRole.WRITER);
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    ClientTestUtils.workspaceRoleWaitForPropagation(workspaceReader, projectId);
    ClientTestUtils.workspaceRoleWaitForPropagation(privateResourceUser, projectId);

    // Create a private bucket, which privateResourceUser assigns to themselves.
    // Cloud IAM permissions may take several minutes to sync, so we retry this operation until
    // it succeeds.
    CreatedControlledGcpGcsBucket bucket =
        RetryUtils.getWithRetryOnException(() -> createPrivateBucket(privateUserResourceApi));
    UUID resourceId = bucket.getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        bucket.getGcpBucket().getMetadata().getProperties());

    // Retrieve the bucket resource from WSM
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcpGcsBucketResource gotBucket = privateUserResourceApi.getBucket(getWorkspaceId(), resourceId);
    String bucketName = gotBucket.getAttributes().getBucketName();
    assertEquals(bucket.getGcpBucket().getAttributes().getBucketName(), bucketName);
    String expectedBucketName = resourceName + "-" + projectId;
    expectedBucketName =
        expectedBucketName.length() > MAX_BUCKET_NAME_LENGTH
            ? expectedBucketName.substring(0, MAX_BUCKET_NAME_LENGTH)
            : expectedBucketName;
    expectedBucketName =
        expectedBucketName.endsWith("-")
            ? expectedBucketName.substring(0, expectedBucketName.length() - 1)
            : expectedBucketName;
    assertEquals(expectedBucketName, bucketName);

    // Assert the bucket is assigned to privateResourceUser, even though resource user was
    // not specified
    assertEquals(
        privateResourceUser.userEmail,
        gotBucket
            .getMetadata()
            .getControlledResourceMetadata()
            .getPrivateResourceUser()
            .getUserName());

    // Creation of the tester will test that privateResourceUser
    // has ControlledResourceIamRole.EDITOR permissions on the bucket
    try (GcsBucketAccessTester tester =
        new GcsBucketAccessTester(privateResourceUser, bucketName, projectId)) {
      // workspace owner and workspace reader can do nothing
      tester.assertAccess(testUser, null);
      tester.assertAccess(workspaceReader, null);
    }

    // Any workspace user should be able to enumerate all buckets, even though they can't access
    // their contents.
    ResourceApi readerApi = ClientTestUtils.getResourceClient(workspaceReader, server);
    ResourceList bucketList =
        readerApi.enumerateResources(
            getWorkspaceId(), 0, 5, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED);
    assertEquals(1, bucketList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_BUCKET, bucketList);

    // Workspace owner has DELETER role and can delete the bucket through WSM
    var ownerDeleteResult = deleteBucket(workspaceOwnerResourceApi, resourceId);
    ClientTestUtils.assertJobSuccess(
        "owner delete bucket",
        ownerDeleteResult.getJobReport(),
        ownerDeleteResult.getErrorReport());

    // verify the bucket was deleted from WSM metadata
    ApiException bucketIsMissing =
        assertThrows(
            ApiException.class,
            () -> workspaceOwnerResourceApi.getBucket(getWorkspaceId(), resourceId),
            "Incorrectly found a deleted bucket!");
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, bucketIsMissing.getCode());

    // also verify it was deleted from GCP
    Storage ownerStorageClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    Bucket maybeBucket = ownerStorageClient.get(bucketName);
    assertNull(maybeBucket);

    // Supply all private user parameters
    PrivateResourceUser privateUserFull =
        new PrivateResourceUser()
            .userName(privateResourceUser.userEmail)
            .privateResourceIamRole(ControlledResourceIamRole.READER);

    var ex =
        assertThrows(
            ApiException.class,
            () ->
                GcsBucketUtils.makeControlledGcsBucket(
                    privateUserResourceApi,
                    getWorkspaceId(),
                    RESOURCE_PREFIX + UUID.randomUUID(),
                    /* bucketName= */ null,
                    AccessScope.PRIVATE_ACCESS,
                    ManagedBy.USER,
                    CloningInstructionsEnum.NOTHING,
                    privateUserFull));
    assertThat(
        ex.getMessage(),
        containsString(
            "PrivateResourceUser can only be specified by applications for private resources"));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, ex.getCode());

    // Supply just the roles, but no email
    PrivateResourceUser privateUserNoEmail =
        new PrivateResourceUser()
            .userName(null)
            .privateResourceIamRole(ControlledResourceIamRole.READER);

    ex =
        assertThrows(
            ApiException.class,
            () ->
                GcsBucketUtils.makeControlledGcsBucket(
                    privateUserResourceApi,
                    getWorkspaceId(),
                    RESOURCE_PREFIX + UUID.randomUUID(),
                    /* bucketName= */ null,
                    AccessScope.PRIVATE_ACCESS,
                    ManagedBy.USER,
                    CloningInstructionsEnum.NOTHING,
                    privateUserNoEmail));
    TestUtils.assertContains(ex.getMessage(), "MethodArgumentNotValidException");
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, ex.getCode());

    String uniqueBucketName = String.format("terra-%s-bucket", UUID.randomUUID());
    CreatedControlledGcpGcsBucket bucketWithBucketNameSpecified =
        GcsBucketUtils.makeControlledGcsBucket(
            privateUserResourceApi,
            getWorkspaceId(),
            RESOURCE_PREFIX + UUID.randomUUID(),
            /* bucketName= */ uniqueBucketName,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            null);
    assertEquals(
        uniqueBucketName,
        bucketWithBucketNameSpecified.getGcpBucket().getAttributes().getBucketName());
    deleteBucket(workspaceOwnerResourceApi, bucketWithBucketNameSpecified.getResourceId());
  }

  private CreatedControlledGcpGcsBucket createPrivateBucket(ControlledGcpResourceApi resourceApi)
      throws Exception {
    return makeControlledGcsBucketUserPrivate(
        resourceApi, getWorkspaceId(), resourceName, CloningInstructionsEnum.NOTHING);
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
