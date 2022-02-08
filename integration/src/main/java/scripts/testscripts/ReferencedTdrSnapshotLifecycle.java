package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.DataRepoUtils.updateDataRepoSnapshotReferenceResource;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.ParameterKeys;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ReferencedTdrSnapshotLifecycle extends WorkspaceAllocateTestScriptBase {

  private String tdrInstance;
  private String snapshotId;
  private String snapshotId2;
  private UUID snapshotResourceId;
  private TestUserSpecification partialAccessUser;
  private UUID destinationWorkspaceId;

  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    tdrInstance = ParameterUtils.getDataRepoInstance(parameters);
    snapshotId = ParameterUtils.getDataRepoSnapshot(parameters);
    snapshotId2 =
        ParameterUtils.getParamOrThrow(
            parameters, ParameterKeys.DATA_REPO_ALTERNATE_SNAPSHOT_PARAMETER);
  }

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    this.partialAccessUser = testUsers.get(1);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGpcResourceClient(testUser, server);
    // Add the "partial access" user as a workspace reader. This does not give them access to any
    // underlying referenced resources.
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(partialAccessUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // Create the reference
    DataRepoSnapshotResource snapshotResource =
        DataRepoUtils.makeDataRepoSnapshotReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            snapshotId,
            tdrInstance);
    snapshotResourceId = snapshotResource.getMetadata().getResourceId();

    // Read the reference
    DataRepoSnapshotResource fetchedSnapshot =
        referencedGcpResourceApi.getDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);
    assertEquals(snapshotResource, fetchedSnapshot);

    // Create a second workspace to clone the reference into, owned by the same user
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    // Clone references
    CloneReferencedGcpDataRepoSnapshotResourceResult snapshotCloneResult =
        referencedGcpResourceApi.cloneGcpDataRepoSnapshotReference(
            new CloneReferencedResourceRequestBody()
                .destinationWorkspaceId(destinationWorkspaceId)
                .cloningInstructions(CloningInstructionsEnum.REFERENCE),
            getWorkspaceId(),
            snapshotResourceId);
    assertEquals(getWorkspaceId(), snapshotCloneResult.getSourceWorkspaceId());
    logger.info("Snapshot resource: " + snapshotResource.toString());
    logger.info("Clone result: " + snapshotCloneResult.toString());
    assertEquals(
        snapshotResource.getAttributes(), snapshotCloneResult.getResource().getAttributes());

    // Validate snapshot access
    ResourceApi ownerResourceApi = ClientTestUtils.getResourceClient(testUser, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    assertTrue(ownerResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    // Partial-access user cannot access snapshot 1. They can access the second snapshot.
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));

    // Update reference
    testUpdateReference(referencedGcpResourceApi);

    // Delete the reference
    referencedGcpResourceApi.deleteDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testUpdateReference(ReferencedGcpResourceApi ownerApi) throws Exception {
    ReferencedGcpResourceApi partialAccessApi =
        ClientTestUtils.getReferencedGpcResourceClient(partialAccessUser, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    // Update snapshot's name and description
    String newSnapshotReferenceName = "newSnapshotReferenceName";
    String newSnapshotReferenceDescription = "a new description of another snapshot reference";
    updateDataRepoSnapshotReferenceResource(
        ownerApi,
        getWorkspaceId(),
        snapshotResourceId,
        newSnapshotReferenceName,
        newSnapshotReferenceDescription,
        /*instanceId=*/ null,
        /*snapshot=*/ null);
    DataRepoSnapshotResource snapshotResource =
        ownerApi.getDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResource.getMetadata().getName());
    assertEquals(newSnapshotReferenceDescription, snapshotResource.getMetadata().getDescription());
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));

    assertThrows(
        ApiException.class,
        () ->
            updateDataRepoSnapshotReferenceResource(
                partialAccessApi,
                getWorkspaceId(),
                snapshotResourceId,
                newSnapshotReferenceName,
                newSnapshotReferenceDescription,
                /*instanceId=*/ null,
                snapshotId2));
    updateDataRepoSnapshotReferenceResource(
        ownerApi,
        getWorkspaceId(),
        snapshotResourceId,
        newSnapshotReferenceName,
        newSnapshotReferenceDescription,
        /*instanceId=*/ null,
        snapshotId2);
    DataRepoSnapshotResource snapshotResourceSecondUpdate =
        ownerApi.getDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResourceSecondUpdate.getMetadata().getName());
    assertEquals(
        newSnapshotReferenceDescription,
        snapshotResourceSecondUpdate.getMetadata().getDescription());
    assertEquals(snapshotId2, snapshotResourceSecondUpdate.getAttributes().getSnapshot());
    assertEquals(tdrInstance, snapshotResourceSecondUpdate.getAttributes().getInstanceName());
    assertTrue(partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceId != null) {
      workspaceApi.deleteWorkspace(destinationWorkspaceId);
    }
  }
}
