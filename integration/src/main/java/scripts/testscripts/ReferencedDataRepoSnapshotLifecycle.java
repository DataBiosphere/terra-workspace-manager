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
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CommonResourceFieldsUtil;
import scripts.utils.DataRepoUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.ParameterKeys;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ReferencedDataRepoSnapshotLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedDataRepoSnapshotLifecycle.class);

  private String tdrInstance;
  private String snapshotId;
  private String snapshotId2;
  private UUID snapshotResourceId;
  private TestUserSpecification partialAccessUser;
  private UUID destinationWorkspaceId;

  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    tdrInstance = ParameterUtils.getDataRepoInstance(parametersMap);
    snapshotId = ParameterUtils.getDataRepoSnapshot(parametersMap);
    snapshotId2 =
        ParameterUtils.getParamOrThrow(
            parametersMap, ParameterKeys.DATA_REPO_ALTERNATE_SNAPSHOT_PARAMETER);
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
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);
    // Add the "partial access" user as a workspace reader. This does not give them access to any
    // underlying referenced resources.
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), partialAccessUser, IamRole.READER);

    // Create the reference
    DataRepoSnapshotResource snapshotResource =
        DataRepoUtils.makeDataRepoSnapshotReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            snapshotId,
            tdrInstance);
    snapshotResourceId = snapshotResource.getMetadata().getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        snapshotResource.getMetadata().getProperties());

    // Get the reference
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    testGetReference(snapshotResource, referencedGcpResourceApi, resourceApi);

    // Create a second workspace to clone the reference into, owned by the same user
    testCloneReference(snapshotResource, referencedGcpResourceApi, workspaceApi);

    // Validate snapshot access
    testValidateReference(testUser);

    // Update reference
    testUpdateReference(referencedGcpResourceApi);

    // Delete the reference
    referencedGcpResourceApi.deleteDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testGetReference(
      DataRepoSnapshotResource snapshotResource,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ResourceApi resourceApi)
      throws Exception {
    // Read the reference by id
    DataRepoSnapshotResource snapshotFetchedById =
        referencedGcpResourceApi.getDataRepoSnapshotReference(getWorkspaceId(), snapshotResourceId);
    assertEquals(snapshotResource, snapshotFetchedById);

    // Read the reference by name
    DataRepoSnapshotResource snapshotFetchedByName =
        referencedGcpResourceApi.getDataRepoSnapshotReferenceByName(
            getWorkspaceId(), snapshotResource.getMetadata().getName());
    assertEquals(snapshotResource, snapshotFetchedByName);

    // Enumerate the reference
    ResourceList referenceList =
        resourceApi.enumerateResources(
            getWorkspaceId(), 0, 5, /* referenceType= */ null, /* stewardShipType= */ null);
    assertEquals(1, referenceList.getResources().size());
    assertEquals(
        StewardshipType.REFERENCED,
        referenceList.getResources().get(0).getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.DATA_REPO_SNAPSHOT,
        referenceList.getResources().get(0).getMetadata().getResourceType());
  }

  private void testCloneReference(
      DataRepoSnapshotResource snapshotResource,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      WorkspaceApi workspaceApi)
      throws Exception {
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
    assertEquals(
        snapshotResource.getAttributes(), snapshotCloneResult.getResource().getAttributes());
  }

  private void testValidateReference(TestUserSpecification owner) throws Exception {
    ResourceApi ownerResourceApi = ClientTestUtils.getResourceClient(owner, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    assertTrue(ownerResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    // Partial-access user cannot access snapshot 1. They can access the second snapshot.
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
  }

  private void testUpdateReference(ReferencedGcpResourceApi ownerApi) throws Exception {
    logger.info("Entering testUpdateReference");
    ReferencedGcpResourceApi partialAccessApi =
        ClientTestUtils.getReferencedGcpResourceClient(partialAccessUser, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    // Update snapshot's name and description
    String newSnapshotReferenceName = "newSnapshotReferenceName";
    String newSnapshotReferenceDescription = "a new description of another snapshot reference";
    logger.info("Update only name and description");
    DataRepoSnapshotResource snapshotResource =
        updateDataRepoSnapshotReferenceResource(
            ownerApi,
            getWorkspaceId(),
            snapshotResourceId,
            newSnapshotReferenceName,
            newSnapshotReferenceDescription,
            /* instanceId= */ null,
            /* snapshot= */ null,
            CloningInstructionsEnum.NOTHING);
    assertEquals(newSnapshotReferenceName, snapshotResource.getMetadata().getName());
    assertEquals(newSnapshotReferenceDescription, snapshotResource.getMetadata().getDescription());
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), snapshotResourceId));
    assertEquals(
        CloningInstructionsEnum.NOTHING, snapshotResource.getMetadata().getCloningInstructions());
    assertThrows(
        ApiException.class,
        () ->
            updateDataRepoSnapshotReferenceResource(
                partialAccessApi,
                getWorkspaceId(),
                snapshotResourceId,
                newSnapshotReferenceName,
                newSnapshotReferenceDescription,
                /* instanceId= */ null,
                snapshotId2,
                /* cloningInstructions= */ null));
    DataRepoSnapshotResource snapshotResourceSecondUpdate =
        updateDataRepoSnapshotReferenceResource(
            ownerApi,
            getWorkspaceId(),
            snapshotResourceId,
            newSnapshotReferenceName,
            newSnapshotReferenceDescription,
            /* instanceId= */ null,
            snapshotId2,
            /* cloningInstructions= */ null);
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
