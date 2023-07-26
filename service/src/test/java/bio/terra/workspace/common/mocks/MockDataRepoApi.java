package bio.terra.workspace.common.mocks;

import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class MockDataRepoApi {

  public static final String CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT =
      CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT =
      REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT + "/clone";

  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private ObjectMapper objectMapper;

  public ApiDataRepoSnapshotResource createReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      String resourceName,
      String instanceName,
      String snapshot)
      throws Exception {
    ApiDataRepoSnapshotAttributes creationParameters =
        new ApiDataRepoSnapshotAttributes().instanceName(instanceName).snapshot(snapshot);
    ApiCreateDataRepoSnapshotReferenceRequestBody request =
        new ApiCreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName)
                    .cloningInstructions(cloningInstructions))
            .snapshot(creationParameters);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public void deleteReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT);
  }

  public ApiDataRepoSnapshotResource getReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public ApiDataRepoSnapshotResource updateReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      String newSnapshot,
      String newInstanceName,
      ApiCloningInstructionsEnum newCloningInstruction)
      throws Exception {
    ApiUpdateDataRepoSnapshotReferenceRequestBody requestBody =
        new ApiUpdateDataRepoSnapshotReferenceRequestBody()
            .name(newName)
            .description(newDescription)
            .cloningInstructions(newCloningInstruction)
            .snapshot(newSnapshot)
            .instanceName(newInstanceName);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public ApiDataRepoSnapshotResource cloneReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedDataRepoSnapshot(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiDataRepoSnapshotResource cloneReferencedDataRepoSnapshot(
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
            CLONE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT,
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
        .readValue(serializedResponse, ApiCloneReferencedGcpDataRepoSnapshotResourceResult.class)
        .getResource();
  }
}
