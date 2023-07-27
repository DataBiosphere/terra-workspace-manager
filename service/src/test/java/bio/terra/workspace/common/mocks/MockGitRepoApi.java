package bio.terra.workspace.common.mocks;

import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.generated.model.ApiCloneReferencedGitRepoResourceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class MockGitRepoApi {

  public static final String CREATE_REFERENCED_GIT_REPOS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos";
  public static final String REFERENCED_GIT_REPOS_PATH_FORMAT =
      CREATE_REFERENCED_GIT_REPOS_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_GIT_REPOS_PATH_FORMAT =
      REFERENCED_GIT_REPOS_PATH_FORMAT + "/clone";

  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private ObjectMapper objectMapper;

  public ApiGitRepoResource createReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String gitRepoUrl)
      throws Exception {
    ApiGitRepoAttributes creationParameters = new ApiGitRepoAttributes().gitRepoUrl(gitRepoUrl);
    ApiCreateGitRepoReferenceRequestBody request =
        new ApiCreateGitRepoReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .gitrepo(creationParameters);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_GIT_REPOS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
  }

  public void deleteReferencedGitRepo(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_GIT_REPOS_PATH_FORMAT);
  }

  public ApiGitRepoResource getReferencedGitRepo(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, REFERENCED_GIT_REPOS_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
  }

  public ApiGitRepoResource updateReferencedGitRepo(
      UUID workspaceId,
      UUID resourceId,
      String newDisplayName,
      String newDescription,
      String newGitRepoUrl,
      ApiCloningInstructionsEnum cloningInstructionsEnum,
      AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiUpdateGitRepoReferenceRequestBody requestBody = new ApiUpdateGitRepoReferenceRequestBody();
    if (newDisplayName != null) {
      requestBody.name(newDisplayName);
    }
    if (newDescription != null) {
      requestBody.description(newDescription);
    }
    if (newGitRepoUrl != null) {
      requestBody.gitRepoUrl(newGitRepoUrl);
    }
    if (cloningInstructionsEnum != null) {
      requestBody.cloningInstructions(cloningInstructionsEnum);
    }
    return mockWorkspaceV1Api.updateResourceAndExpect(
        ApiGitRepoResource.class,
        REFERENCED_GIT_REPOS_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
  }

  public ApiGitRepoResource cloneReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedGitRepo(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGitRepoResource cloneReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        mockWorkspaceV1Api.cloneReferencedResourceAndExpect(
            userRequest,
            CLONE_REFERENCED_GIT_REPOS_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);
    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGitRepoResourceResult.class)
        .getResource();
  }
}
