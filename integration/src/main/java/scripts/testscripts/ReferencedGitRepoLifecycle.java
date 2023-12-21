package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneReferencedGitRepoResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
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
  private UUID destinationWorkspaceId;
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
            getWorkspaceId(),
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
    referencedGcpResourceApi.deleteGitRepoReference(getWorkspaceId(), gitResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testGetReference(
      GitRepoResource gitResource,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ResourceApi resourceApi)
      throws Exception {
    GitRepoResource fetchedGitResource =
        referencedGcpResourceApi.getGitRepoReference(getWorkspaceId(), gitResourceId);
    assertEquals(gitResource, fetchedGitResource);

    // Enumerate the reference
    ResourceList referenceList =
        resourceApi.enumerateResources(
            getWorkspaceId(), 0, 5, /* referenceType= */ null, /* stewardShipType= */ null);
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
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    // Clone references
    CloneReferencedGitRepoResourceResult gitCloneResult =
        referencedGcpResourceApi.cloneGitRepoReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            gitResourceId);
    assertEquals(getWorkspaceId(), gitCloneResult.getSourceWorkspaceId());
    assertEquals(gitResource.getAttributes(), gitCloneResult.getResource().getAttributes());
  }

  private void testUpdateReference(ReferencedGcpResourceApi referencedGcpResourceApi)
      throws Exception {
    String newGitRepoReferenceName = "newGitRepoReferenceName";
    String newGitRepoReferenceDescription = "a new description for git repo reference";
    GitRepoResource updatedResource =
        GitRepoUtils.updateGitRepoReferenceResource(
            referencedGcpResourceApi,
            getWorkspaceId(),
            gitResourceId,
            newGitRepoReferenceName,
            newGitRepoReferenceDescription,
            /* gitCloneUrl= */ null,
            CloningInstructionsEnum.NOTHING);
    assertEquals(newGitRepoReferenceName, updatedResource.getMetadata().getName());
    assertEquals(newGitRepoReferenceDescription, updatedResource.getMetadata().getDescription());
    assertEquals(
        CloningInstructionsEnum.NOTHING, updatedResource.getMetadata().getCloningInstructions());
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
