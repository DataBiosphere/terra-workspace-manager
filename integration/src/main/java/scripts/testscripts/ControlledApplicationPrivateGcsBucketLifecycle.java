package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static scripts.utils.ClientTestUtils.TEST_WSM_APP;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.api.WorkspaceApplicationApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.ApplicationState;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.WorkspaceApplicationDescription;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketAccessTester;
import scripts.utils.GcsBucketUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

/**
 * This test focused on the application-private permissions. The interaction with enabling and
 * disabling is sufficiently tested (I think!) in ControlledApplicationSharedGcsBucketLifecycle. In
 * this test, we make three buckets:
 *
 * <ul>
 *   <li>one with no assigned user, so only the app has any access
 *   <li>one with an assigned user READER
 *   <li>one with an assigned user WRITER
 * </ul>
 *
 * We use the opposite user of the permission just to make it interesting!
 */
public class ControlledApplicationPrivateGcsBucketLifecycle
    extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger =
      LoggerFactory.getLogger(ControlledApplicationPrivateGcsBucketLifecycle.class);
  private TestUserSpecification owner;
  private TestUserSpecification writer;
  private TestUserSpecification reader;
  private TestUserSpecification wsmapp;
  private String bucketName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // The 0th user is the owner of the workspace
    // The 1st user will be a workspace writer
    // The 2nd user will be a workspace reader
    // The 3rd user must be the WSM test application identity
    assertThat(
        "There must be four test users defined for this test.",
        testUsers != null && testUsers.size() == 4);
    this.owner = testUsers.get(0);
    this.writer = testUsers.get(1);
    this.reader = testUsers.get(2);
    this.wsmapp = testUsers.get(3);
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(owner, server);
    ApiClient wsmappApiClient = ClientTestUtils.getClientForTestUser(wsmapp, server);
    WorkspaceApplicationApi ownerWsmAppApi = new WorkspaceApplicationApi(ownerApiClient);
    ControlledGcpResourceApi wsmappResourceApi = new ControlledGcpResourceApi(wsmappApiClient);

    // Owner adds a reader and a writer to the workspace
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), reader, IamRole.READER);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), writer, IamRole.WRITER);

    // Create the cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    assertNotNull(projectId);
    logger.info("Created project {}", projectId);

    // Wait for grantees to have permission
    ClientTestUtils.workspaceRoleWaitForPropagation(reader, projectId);
    ClientTestUtils.workspaceRoleWaitForPropagation(writer, projectId);

    // Enable the application in the workspace
    WorkspaceApplicationDescription applicationDescription =
        ownerWsmAppApi.enableWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);
    assertThat(applicationDescription.getApplicationState(), equalTo(ApplicationState.OPERATING));
    logger.info("Enabled application {} in the workspace {}", TEST_WSM_APP, getWorkspaceId());

    // CASE 1: Create a bucket with no assigned user
    testNoAssignedUser(wsmappResourceApi, projectId);

    // CASE 2: Create a bucket with workspace writer as READER
    testAssignedReader(wsmappResourceApi, projectId);

    // CASE 3: Create a bucket with workspace reader as WRITER
    testAssignedWriter(wsmappResourceApi, projectId);

    // All buckets should be visible to enumeration
    ResourceApi ownerResourceApi = ClientTestUtils.getResourceClient(owner, server);
    ResourceList bucketList =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(), 0, 5, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED);
    assertEquals(3, bucketList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_BUCKET, bucketList);
  }

  private void testNoAssignedUser(ControlledGcpResourceApi resourceApi, String projectId)
      throws Exception {
    String bucketResourceName = RandomStringUtils.random(6, true, false);
    CreatedControlledGcpGcsBucket createdBucket =
        GcsBucketUtils.makeControlledGcsBucketAppPrivate(
            resourceApi,
            getWorkspaceId(),
            bucketResourceName,
            CloningInstructionsEnum.NOTHING,
            null);
    bucketName = createdBucket.getGcpBucket().getAttributes().getBucketName();
    assertNotNull(bucketName);
    logger.info("Created no-assigned-user bucket {}", bucketName);

    try (GcsBucketAccessTester tester = new GcsBucketAccessTester(wsmapp, bucketName, projectId)) {
      tester.checkAccess(wsmapp, ControlledResourceIamRole.EDITOR);
      tester.checkAccess(owner, null);
      // Don't bother testing reader and writer here.
    }
    deleteBucket(resourceApi, createdBucket);
  }

  private void testAssignedReader(ControlledGcpResourceApi resourceApi, String projectId)
      throws Exception {
    ControlledResourceIamRole iamRole = ControlledResourceIamRole.READER;
    PrivateResourceUser privateUser =
        new PrivateResourceUser().privateResourceIamRole(iamRole).userName(writer.userEmail);

    String bucketResourceName = RandomStringUtils.random(6, true, false);
    CreatedControlledGcpGcsBucket createdBucket =
        GcsBucketUtils.makeControlledGcsBucketAppPrivate(
            resourceApi,
            getWorkspaceId(),
            bucketResourceName,
            CloningInstructionsEnum.NOTHING,
            privateUser);
    bucketName = createdBucket.getGcpBucket().getAttributes().getBucketName();
    assertNotNull(bucketName);
    logger.info("Created assigned-reader bucket {}", bucketName);

    try (GcsBucketAccessTester tester = new GcsBucketAccessTester(wsmapp, bucketName, projectId)) {
      tester.checkAccess(wsmapp, ControlledResourceIamRole.EDITOR);
      tester.checkAccess(reader, null);
      tester.checkAccess(writer, ControlledResourceIamRole.READER);
    }
    deleteBucket(resourceApi, createdBucket);
  }

  private void testAssignedWriter(ControlledGcpResourceApi resourceApi, String projectId)
      throws Exception {
    ControlledResourceIamRole iamRole = ControlledResourceIamRole.WRITER;
    PrivateResourceUser privateUser =
        new PrivateResourceUser().privateResourceIamRole(iamRole).userName(reader.userEmail);

    String bucketResourceName = RandomStringUtils.random(6, true, false);
    CreatedControlledGcpGcsBucket createdBucket =
        GcsBucketUtils.makeControlledGcsBucketAppPrivate(
            resourceApi,
            getWorkspaceId(),
            bucketResourceName,
            CloningInstructionsEnum.NOTHING,
            privateUser);
    bucketName = createdBucket.getGcpBucket().getAttributes().getBucketName();
    assertNotNull(bucketName);
    logger.info("Created assigned-writer bucket {}", bucketName);

    try (GcsBucketAccessTester tester = new GcsBucketAccessTester(wsmapp, bucketName, projectId)) {
      tester.checkAccess(wsmapp, ControlledResourceIamRole.EDITOR);
      tester.checkAccess(writer, null);
      tester.checkAccess(reader, ControlledResourceIamRole.WRITER);
    }
    deleteBucket(resourceApi, createdBucket);
  }

  private void deleteBucket(
      ControlledGcpResourceApi resourceApi, CreatedControlledGcpGcsBucket createdBucket)
      throws Exception {
    GcsBucketUtils.deleteControlledGcsBucket(
        createdBucket.getResourceId(), getWorkspaceId(), resourceApi);
    logger.info("Application deleted bucket {}", bucketName);
    bucketName = null;
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
