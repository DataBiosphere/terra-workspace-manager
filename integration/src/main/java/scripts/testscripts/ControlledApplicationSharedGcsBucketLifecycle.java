package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static scripts.utils.ClientTestUtils.TEST_WSM_APP;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.api.WorkspaceApplicationApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ApplicationState;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.WorkspaceApplicationDescription;
import bio.terra.workspace.model.WorkspaceApplicationState;
import com.google.api.client.http.HttpStatusCodes;
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

public class ControlledApplicationSharedGcsBucketLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger =
      LoggerFactory.getLogger(ControlledApplicationSharedGcsBucketLifecycle.class);
  private TestUserSpecification owner;
  private TestUserSpecification writer;
  private TestUserSpecification reader;
  private TestUserSpecification wsmapp;
  private String bucketName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // The 0th user is the owner of the workspace, pulled out in the super class.
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
  public void doUserJourney(TestUserSpecification unused, WorkspaceApi workspaceApi)
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

    // Wait for grantees to get permission
    ClientTestUtils.workspaceRoleWaitForPropagation(reader, projectId);
    ClientTestUtils.workspaceRoleWaitForPropagation(writer, projectId);

    // Create the bucket - should fail because application is not enabled
    String bucketResourceName = RandomStringUtils.random(6, true, false);
    ApiException createBucketFails =
        assertThrows(
            ApiException.class,
            () ->
                GcsBucketUtils.makeControlledGcsBucketAppShared(
                    wsmappResourceApi,
                    getWorkspaceId(),
                    bucketResourceName,
                    CloningInstructionsEnum.NOTHING));
    // TODO: [PF-1208] this should be FORBIDDEN (403), but we are throwing the wrong thing
    assertEquals(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, createBucketFails.getCode());
    logger.info("Failed to create bucket, as expected");

    // Enable the application in the workspace
    WorkspaceApplicationDescription applicationDescription =
        ownerWsmAppApi.enableWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);
    assertThat(applicationDescription.getApplicationState(), equalTo(ApplicationState.OPERATING));
    logger.info("Enabled application in the workspace");

    // Validate that it is enabled
    WorkspaceApplicationDescription retrievedDescription =
        ownerWsmAppApi.getWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);
    assertThat(applicationDescription, equalTo(retrievedDescription));
    assertThat(
        applicationDescription.getWorkspaceApplicationState(),
        equalTo(WorkspaceApplicationState.ENABLED));

    // Create the bucket - should work this time
    CreatedControlledGcpGcsBucket createdBucket =
        GcsBucketUtils.makeControlledGcsBucketAppShared(
            wsmappResourceApi,
            getWorkspaceId(),
            bucketResourceName,
            CloningInstructionsEnum.NOTHING);
    bucketName = createdBucket.getGcpBucket().getAttributes().getBucketName();
    assertNotNull(bucketName);
    logger.info("Created bucket {}", bucketName);

    // Try to disable; should error because you cannot disable an app if it owns resources
    // in the workspace.
    ApiException disableAppFails =
        assertThrows(
            ApiException.class,
            () -> ownerWsmAppApi.disableWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, disableAppFails.getCode());
    logger.info("Failed to disable app, as expected");

    try (GcsBucketAccessTester tester = new GcsBucketAccessTester(wsmapp, bucketName, projectId)) {
      tester.checkAccess(wsmapp, ControlledResourceIamRole.EDITOR);
      tester.checkAccess(owner, ControlledResourceIamRole.WRITER);
      tester.checkAccess(writer, ControlledResourceIamRole.WRITER);
      tester.checkAccess(reader, ControlledResourceIamRole.READER);
    }

    // The reader should be able to enumerate the bucket.
    ResourceApi readerResourceApi = ClientTestUtils.getResourceClient(reader, server);
    ResourceList bucketList =
        readerResourceApi.enumerateResources(
            getWorkspaceId(), 0, 5, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED);
    assertEquals(1, bucketList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_BUCKET, bucketList);

    // Owner cannot delete the bucket through WSM
    ControlledGcpResourceApi ownerResourceApi = new ControlledGcpResourceApi(ownerApiClient);
    ApiException cannotDelete =
        assertThrows(
            ApiException.class,
            () ->
                GcsBucketUtils.deleteControlledGcsBucket(
                    createdBucket.getResourceId(), getWorkspaceId(), ownerResourceApi));
    // TODO: [PF-1208] this should be FORBIDDEN (403), but we are throwing the wrong thing
    assertEquals(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, cannotDelete.getCode());
    logger.info("Owner delete failed as expected");

    // Application can delete the bucket through WSM
    GcsBucketUtils.deleteControlledGcsBucket(
        createdBucket.getResourceId(), getWorkspaceId(), wsmappResourceApi);
    logger.info("Application delete succeeded");
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
