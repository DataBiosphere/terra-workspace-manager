package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsBucketFileResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class ValidateReferencedResources extends DataRepoTestScriptBase {

  private TestUserSpecification secondUser;
  private UUID bqResourceId;
  private UUID bucketResourceId;
  private UUID snapshotResourceId;
  private UUID bucketFileResourceId;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    this.secondUser = testUsers.get(1);

    ApiClient apiClient = ClientTestUtils.getClientForTestUser(testUsers.get(0), server);
    ReferencedGcpResourceApi referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);
    String bqReferenceName = RandomStringUtils.random(6, true, false);
    GcpBigQueryDatasetResource bqReference =
        ResourceMaker.makeBigQueryReference(
            referencedGcpResourceApi, getWorkspaceId(), bqReferenceName);
    bqResourceId = bqReference.getMetadata().getResourceId();
    String bucketReferenceName = RandomStringUtils.random(6, true, false);
    GcpGcsBucketResource bucketReference =
        ResourceMaker.makeGcsBucketReference(
            referencedGcpResourceApi, getWorkspaceId(), bucketReferenceName);
    bucketResourceId = bucketReference.getMetadata().getResourceId();
    String snapshotReferenceName = RandomStringUtils.random(6, true, false);
    DataRepoSnapshotResource snapshotReference =
        ResourceMaker.makeDataRepoSnapshotReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            snapshotReferenceName,
            getDataRepoSnapshotId(),
            getDataRepoInstanceName());
    snapshotResourceId = snapshotReference.getMetadata().getResourceId();

    String bucketFileReferenceName = RandomStringUtils.random(6, /*letters*/true, /*numbers=*/true);
    GcpGcsBucketFileResource bucketFileReference =
        ResourceMaker.makeGcsBucketFileReference(
            referencedGcpResourceApi, getWorkspaceId(), bucketFileReferenceName);
    bucketFileResourceId = bucketFileReference.getMetadata().getResourceId();
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ResourceApi ownerApi = new ResourceApi(ClientTestUtils.getClientForTestUser(testUser, server));
    ResourceApi secondUserApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(secondUser, server));

    // Add second user as workspace reader, though this will not affect permissions on referenced
    // external objects.
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(secondUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // Check that our main test user has access
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketFileResourceId));

    // Check that our secondary test user does not have access
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bqResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    assertFalse(secondUserApi.checkReferenceAccess(getWorkspaceId(), bucketFileResourceId));
  }
}
