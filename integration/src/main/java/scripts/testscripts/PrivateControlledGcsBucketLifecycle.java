package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserPrivate;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
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
import scripts.utils.GcsBucketAccessTester;
import scripts.utils.GcsBucketUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(PrivateControlledGcsBucketLifecycle.class);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;

  private static final String BUCKET_PREFIX = "wsmtestbucket-";
  private static final String RESOURCE_PREFIX = "wsmtestresource-";

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

    // Create a private bucket, which privateResourceUser assigns to themselves.
    // Cloud IAM permissions may take several minutes to sync, so we retry this operation until
    // it succeeds.
    CreatedControlledGcpGcsBucket bucket =
        ClientTestUtils.getWithRetryOnException(() -> createPrivateBucket(privateUserResourceApi));
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource from WSM
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcpGcsBucketResource gotBucket = privateUserResourceApi.getBucket(getWorkspaceId(), resourceId);
    String bucketName = gotBucket.getAttributes().getBucketName();
    assertEquals(bucket.getGcpBucket().getAttributes().getBucketName(), bucketName);

    // Assert the bucket is assigned to privateResourceUser, even though resource user was
    // not specified
    assertEquals(
        privateResourceUser.userEmail,
        gotBucket
            .getMetadata()
            .getControlledResourceMetadata()
            .getPrivateResourceUser()
            .getUserName());

    try (GcsBucketAccessTester tester =
        new GcsBucketAccessTester(privateResourceUser, bucketName, projectId)) {
      tester.checkAccessWait(privateResourceUser, ControlledResourceIamRole.EDITOR);
      // workspace owner can do nothing
      tester.checkAccess(testUser, null);
      tester.checkAccess(workspaceReader, null);
    }

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

    // TODO: PF-1218 - change these to negative tests - should error - when
    //  the ticket is complete. These exercise two create cases with currently
    //  valid combinations of private user.
    PrivateResourceIamRoles roles = new PrivateResourceIamRoles();
    roles.add(ControlledResourceIamRole.READER);

    // Supply all private user parameters
    PrivateResourceUser privateUserFull =
        new PrivateResourceUser()
            .userName(privateResourceUser.userEmail)
            .privateResourceIamRoles(roles);

    CreatedControlledGcpGcsBucket userFullBucket =
        GcsBucketUtils.makeControlledGcsBucket(
            privateUserResourceApi,
            getWorkspaceId(),
            RESOURCE_PREFIX + UUID.randomUUID().toString(),
            /*bucketName=*/ null,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            privateUserFull);
    assertNotNull(userFullBucket.getGcpBucket().getAttributes().getBucketName());
    deleteBucket(workspaceOwnerResourceApi, userFullBucket.getResourceId());

    // Supply just the roles, but no email
    PrivateResourceUser privateUserNoEmail =
        new PrivateResourceUser().userName(null).privateResourceIamRoles(roles);

    CreatedControlledGcpGcsBucket userNoEmailBucket =
        GcsBucketUtils.makeControlledGcsBucket(
            privateUserResourceApi,
            getWorkspaceId(),
            RESOURCE_PREFIX + UUID.randomUUID().toString(),
            /*bucketName=*/ null,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            privateUserNoEmail);
    assertNotNull(userNoEmailBucket.getGcpBucket().getAttributes().getBucketName());
    deleteBucket(workspaceOwnerResourceApi, userNoEmailBucket.getResourceId());

    String uniqueBucketName =
        String.format("terra_%s_bucket", UUID.randomUUID().toString().replace("-", "_"));
    CreatedControlledGcpGcsBucket bucketWithBucketNameSpecified =
        GcsBucketUtils.makeControlledGcsBucket(
            privateUserResourceApi,
            getWorkspaceId(),
            RESOURCE_PREFIX + UUID.randomUUID().toString(),
            /*bucketName=*/ uniqueBucketName,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            privateUserFull);
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
