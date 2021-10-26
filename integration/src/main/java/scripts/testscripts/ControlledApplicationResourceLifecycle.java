package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.api.WorkspaceApplicationApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.ApplicationState;
import bio.terra.workspace.model.WorkspaceApplicationDescription;
import bio.terra.workspace.model.WorkspaceApplicationState;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledApplicationResourceLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger =
      LoggerFactory.getLogger(ControlledApplicationResourceLifecycle.class);

  private TestUserSpecification owner;
  private TestUserSpecification writer;
  private TestUserSpecification reader;
  private TestUserSpecification wsmapp;
  private String bucketName;
  private String resourceName;

  // We may want this to be a test parameter
  // It has to match what is in the config or in the helm
  private static final UUID TEST_WSM_APP = UUID.fromString("E4C0924A-3D7D-4D3D-8DE4-3D2CF50C3818");

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // The 0th user is the owner of the workspace, pulled out in the super class.
    // The 1st user will be a workspace writer
    // The 2nd user will be a workspace reader
    // The 3rd user must be the WSM test application identity
    assertThat(
        "There must be at least four test users defined for this test.",
        testUsers != null && testUsers.size() > 3);
    this.owner = testUsers.get(0);
    this.writer = testUsers.get(1);
    this.reader = testUsers.get(2);
    this.wsmapp = testUsers.get(3);
    String nameSuffix = UUID.randomUUID().toString();
    this.bucketName = BUCKET_PREFIX + nameSuffix;
    this.resourceName = RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(owner, server);
    WorkspaceApplicationApi wsmAppApi = new WorkspaceApplicationApi(ownerApiClient);

    // For this PR, the test just exercises the REST API, but does not do resource creation.
    // TODO: PF-1038 - add and debug resource creation
    WorkspaceApplicationDescription applicationDescription =
        wsmAppApi.enableWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);

    assertThat(applicationDescription.getApplicationState(), equalTo(ApplicationState.OPERATING));

    WorkspaceApplicationDescription retrievedDescription =
        wsmAppApi.getWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);
    assertThat(applicationDescription, equalTo(retrievedDescription));

    assertThat(
        applicationDescription.getWorkspaceApplicationState(),
        equalTo(WorkspaceApplicationState.ENABLED));

    WorkspaceApplicationDescription disabledDescription =
        wsmAppApi.disableWorkspaceApplication(getWorkspaceId(), TEST_WSM_APP);
    assertThat(
        disabledDescription.getWorkspaceApplicationState(),
        equalTo(WorkspaceApplicationState.DISABLED));

    // TODO: PF-1038 - remember to clear this when we delete the bucket
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
