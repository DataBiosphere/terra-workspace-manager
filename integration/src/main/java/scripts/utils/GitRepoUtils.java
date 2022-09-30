package scripts.utils;

import static scripts.utils.CommonResourceFieldsUtil.makeReferencedResourceCommonFields;

import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateGitRepoReferenceRequestBody;
import bio.terra.workspace.model.GitRepoAttributes;
import bio.terra.workspace.model.GitRepoResource;
import bio.terra.workspace.model.UpdateGitRepoReferenceRequestBody;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitRepoUtils {
  private static final Logger logger = LoggerFactory.getLogger(GitRepoUtils.class);

  public static GitRepoResource updateGitRepoReferenceResource(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String gitRepoUrl,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws ApiException {
    UpdateGitRepoReferenceRequestBody body = new UpdateGitRepoReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (gitRepoUrl != null) {
      body.setGitRepoUrl(gitRepoUrl);
    }
    if (cloningInstructions != null) {
      body.setCloningInstructions(cloningInstructions);
    }
    return resourceApi.updateGitRepoReference(body, workspaceUuid, resourceId);
  }

  /**
   * Calls WSM to create a referenced Git repository in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GitRepoResource makeGitRepoReference(
      GitRepoAttributes attributes,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceUuid,
      String name)
      throws Exception {

    CreateGitRepoReferenceRequestBody body =
        new CreateGitRepoReferenceRequestBody()
            .metadata(makeReferencedResourceCommonFields(name, CloningInstructionsEnum.REFERENCE))
            .gitrepo(new GitRepoAttributes().gitRepoUrl(attributes.getGitRepoUrl()));
    logger.info("Making git repo reference of {} with name {}", attributes.getGitRepoUrl(), name);
    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createGitRepoReference(body, workspaceUuid));
  }
}
