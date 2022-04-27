package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneReferencedGitRepoResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.GitRepoAttributes;
import bio.terra.workspace.model.GitRepoResource;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import scripts.utils.ClientTestUtils;
import scripts.utils.GitRepoUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ReferencedGitRepoLifecycle extends WorkspaceAllocateTestScriptBase {

  private GitRepoAttributes gitRepoAttributes;
  private UUID destinationWorkspaceUuid;
  private UUID gitResourceId;

  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    gitRepoAttributes = ParameterUtils.getSshGitRepoReference(parametersMap);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);
    // Create the reference
    GitRepoResource gitResource =
        GitRepoUtils.makeGitRepoReference(
            gitRepoAttributes,
            referencedGcpResourceApi,
            getWorkspaceUuid(),
            MultiResourcesUtils.makeName());
    gitResourceId = gitResource.getMetadata().getResourceId();

    // Read the reference
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    testGetReference(gitResource, referencedGcpResourceApi, resourceApi);

    // Clone the reference
    testCloneReference(gitResource, referencedGcpResourceApi, workspaceApi);

    // No validation checks yet, we don't validate access to git repos.

    // Update the reference
    testUpdateReference(referencedGcpResourceApi);

    // Delete the reference
    referencedGcpResourceApi.deleteGitRepoReference(getWorkspaceUuid(), gitResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceUuid(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testGetReference(
      GitRepoResource gitResource,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ResourceApi resourceApi)
      throws Exception {
    GitRepoResource fetchedGitResource =
        referencedGcpResourceApi.getGitRepoReference(getWorkspaceUuid(), gitResourceId);
    assertEquals(gitResource, fetchedGitResource);

    // Enumerate the reference
    ResourceList referenceList =
        resourceApi.enumerateResources(
            getWorkspaceUuid(), 0, 5, /*referenceType=*/ null, /*stewardShipType=*/ null);
    assertEquals(1, referenceList.getResources().size());
    assertEquals(
        StewardshipType.REFERENCED,
        referenceList.getResources().get(0).getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.GIT_REPO, referenceList.getResources().get(0).getMetadata().getResourceType());
  }

  private void testCloneReference(
      GitRepoResource gitResource,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      WorkspaceApi workspaceApi)
      throws Exception {
    // Create a second workspace to clone the reference into, owned by the same user
    destinationWorkspaceUuid = UUID.randomUUID();
    createWorkspace(destinationWorkspaceUuid, getSpendProfileId(), workspaceApi);
    // Clone references
    CloneReferencedGitRepoResourceResult gitCloneResult =
        referencedGcpResourceApi.cloneGitRepoReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceUuid(destinationWorkspaceUuid),
            getWorkspaceUuid(),
            gitResourceId);
    assertEquals(getWorkspaceUuid(), gitCloneResult.getSourceWorkspaceUuid());
    assertEquals(gitResource.getAttributes(), gitCloneResult.getResource().getAttributes());
  }

  private void testUpdateReference(ReferencedGcpResourceApi referencedGcpResourceApi)
      throws Exception {
    String newGitRepoReferenceName = "newGitRepoReferenceName";
    String newGitRepoReferenceDescription = "a new description for git repo reference";
    GitRepoUtils.updateGitRepoReferenceResource(
        referencedGcpResourceApi,
        getWorkspaceUuid(),
        gitResourceId,
        newGitRepoReferenceName,
        newGitRepoReferenceDescription,
        /*gitCloneUrl=*/ null);
    GitRepoResource updatedResource =
        referencedGcpResourceApi.getGitRepoReference(getWorkspaceUuid(), gitResourceId);
    assertEquals(newGitRepoReferenceName, updatedResource.getMetadata().getName());
    assertEquals(newGitRepoReferenceDescription, updatedResource.getMetadata().getDescription());
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceUuid != null) {
      workspaceApi.deleteWorkspace(destinationWorkspaceUuid);
    }
  }
}
