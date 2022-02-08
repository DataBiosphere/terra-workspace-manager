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


  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    gitRepoAttributes = ParameterUtils.getSshGitRepoReference(parameters);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi = ClientTestUtils.getReferencedGpcResourceClient(testUser, server);
    // Create the reference
    GitRepoResource gitResource = GitRepoUtils.makeGitRepoReference(gitRepoAttributes, referencedGcpResourceApi, getWorkspaceId(),
        MultiResourcesUtils.makeName());
    UUID gitResourceId = gitResource.getMetadata().getResourceId();

    // Read the reference
    GitRepoResource fetchedGitResource = referencedGcpResourceApi.getGitRepoReference(getWorkspaceId(), gitResourceId);
    assertEquals(gitResource, fetchedGitResource);

    // Create a second workspace to clone the reference into, owned by the same user
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    // Clone references
    CloneReferencedGitRepoResourceResult gitCloneResult = referencedGcpResourceApi.cloneGitRepoReference(new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId), getWorkspaceId(), gitResourceId);
    assertEquals(getWorkspaceId(), gitCloneResult.getSourceWorkspaceId());
    assertEquals(gitResource.getAttributes(), gitCloneResult.getResource().getAttributes());

    // No validation checks yet, we don't validate access to git repos.

    // Update the reference
    String newGitRepoReferenceName = "newGitRepoReferenceName";
    String newGitRepoReferenceDescription = "a new description for git repo reference";
    GitRepoUtils.updateGitRepoReferenceResource(
        referencedGcpResourceApi,
        getWorkspaceId(),
        gitResourceId,
        newGitRepoReferenceName,
        newGitRepoReferenceDescription,
        /*gitCloneUrl=*/ null);
    GitRepoResource updatedResource = referencedGcpResourceApi.getGitRepoReference(getWorkspaceId(), gitResourceId);
    assertEquals(newGitRepoReferenceName, updatedResource.getMetadata().getName());
    assertEquals(newGitRepoReferenceDescription, updatedResource.getMetadata().getDescription());

    // Delete the reference
    referencedGcpResourceApi.deleteGitRepoReference(getWorkspaceId(), gitResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    ResourceList enumerateResult = resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceId != null) {
      workspaceApi.deleteWorkspace(destinationWorkspaceId);
    }
  }
}
