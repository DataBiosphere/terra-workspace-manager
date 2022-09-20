package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.MockMvcUtils.RESOURCE_PROPERTIES_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiPropertyKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ResourceApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired WorkspaceConnectedTestUtils connectedTestUtils;

  private UUID workspaceId;

  @BeforeEach
  public void setUp() throws Exception {
    workspaceId =
        connectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
  }

  @AfterEach
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Nested
  class UpdateResourceProperties {
    @Test
    public void updateResourceProperties_newPropertiesAdded() throws Exception {
      // Create resource with no properties.
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();
      var folderIdKey = "terra_workspace_folder_id";
      Map<String, String> newProperties =
          Map.of(
              "terra_workspace_folder_id",
              UUID.randomUUID().toString(),
              "data_type",
              "workflow_output");
      Map<String, String> expectedProperties = new HashMap();
      expectedProperties.putAll(newProperties);

      // Add two more properties with key terra_workspace_folder_id and data_type.
      updateResourcePropertiesExpectCode(
          workspaceId, resourceId, newProperties, HttpStatus.SC_NO_CONTENT);

      // Get the updated resource and assert that the new properties are added.
      ApiGcpBigQueryDatasetResource updatedResource =
          mockMvcUtils.getBigQueryDataset(
              userAccessUtils.defaultUserAuthRequest(), workspaceId, resourceId);
      assertEquals(
          expectedProperties,
          convertApiPropertyToMap(updatedResource.getMetadata().getProperties()));

      var newFolderId = UUID.randomUUID();
      // Change property terra_workspace_folder_id to new UUID.
      updateResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          Map.of(folderIdKey, newFolderId.toString()),
          HttpStatus.SC_NO_CONTENT);

      // Get the updated resource and assert terra_workspace_folder_id has new UUID.
      ApiGcpBigQueryDatasetResource updatedResource2 =
          mockMvcUtils.getBigQueryDataset(
              userAccessUtils.defaultUserAuthRequest(), workspaceId, resourceId);
      assertEquals(
          newFolderId.toString(),
          convertApiPropertyToMap(updatedResource2.getMetadata().getProperties()).get(folderIdKey));
    }

    @Test
    public void updateResourceProperties_resourceDoesNotExist_throws404() throws Exception {
      updateResourcePropertiesExpectCode(
          workspaceId,
          /*resourceId=*/ UUID.randomUUID(),
          Map.of("foo1", "bar1"),
          HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void updateResourceProperties_propertiesIsEmpty_throws400() throws Exception {
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();

      updateResourcePropertiesExpectCode(
          workspaceId, resourceId, Map.of(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void updateResourceProperties_readOnlyPermission_throws403() throws Exception {
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();
      mockMvcUtils.grantRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId,
          WsmIamRole.READER,
          userAccessUtils.getSecondUserEmail());

      updateResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          Map.of("foo", "bar"),
          userAccessUtils.secondUserAuthRequest(),
          HttpStatus.SC_FORBIDDEN);
    }
  }

  @Nested
  class DeleteResourceProperties {

    @Test
    public void deleteResourceProperties_propertiesDeleted() throws Exception {
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();
      updateResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          Map.of("foo", "bar", "sweet", "cake", "cute", "puppy"),
          HttpStatus.SC_NO_CONTENT);

      deleteResourcePropertiesExpectCode(
          workspaceId, resourceId, List.of("foo", "sweet", "cute"), HttpStatus.SC_NO_CONTENT);

      ApiGcpBigQueryDatasetResource updatedResource =
          mockMvcUtils.getBigQueryDataset(
              userAccessUtils.defaultUserAuthRequest(), workspaceId, resourceId);
      assertTrue(convertApiPropertyToMap(updatedResource.getMetadata().getProperties()).isEmpty());
    }

    @Test
    public void deleteResourceProperties_resourceDoesNotExist_throws404() throws Exception {
      deleteResourcePropertiesExpectCode(
          workspaceId, /*resourceId=*/ UUID.randomUUID(), List.of("foo"), HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void deleteResourceProperties_propertiesIsEmpty_throws400() throws Exception {
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();

      deleteResourcePropertiesExpectCode(
          workspaceId, resourceId, List.of(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void deleteResourceProperties_readOnlyPermission_throws403() throws Exception {
      ApiCreatedControlledGcpBigQueryDataset resource =
          mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);
      UUID resourceId = resource.getResourceId();
      updateResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          Map.of("foo", "bar", "sweet", "cake", "cute", "puppy"),
          HttpStatus.SC_NO_CONTENT);
      mockMvcUtils.grantRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId,
          WsmIamRole.READER,
          userAccessUtils.getSecondUserEmail());

      deleteResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          List.of("foo"),
          userAccessUtils.secondUserAuthRequest(),
          HttpStatus.SC_FORBIDDEN);
    }

    private void deleteResourcePropertiesExpectCode(
        UUID workspaceId, UUID resourceId, List<String> propertyKeysToDelete, int code)
        throws Exception {
      deleteResourcePropertiesExpectCode(
          workspaceId,
          resourceId,
          propertyKeysToDelete,
          userAccessUtils.defaultUserAuthRequest(),
          code);
    }

    private void deleteResourcePropertiesExpectCode(
        UUID workspaceId,
        UUID resourceId,
        List<String> propertyKeysToDelete,
        AuthenticatedUserRequest userRequest,
        int code)
        throws Exception {
      mockMvc
          .perform(
              addJsonContentType(
                  addAuth(
                      patch(
                              String.format(
                                  RESOURCE_PROPERTIES_V1_PATH_FORMAT, workspaceId, resourceId))
                          .contentType(MediaType.APPLICATION_JSON_VALUE)
                          .accept(MediaType.APPLICATION_JSON)
                          .characterEncoding("UTF-8")
                          .content(getDeleteResourcePropertiesInJson(propertyKeysToDelete)),
                      userRequest)))
          .andExpect(status().is(code));
    }

    private String getDeleteResourcePropertiesInJson(List<String> properties)
        throws JsonProcessingException {
      ApiPropertyKeys apiPropertyKeys = new ApiPropertyKeys();
      apiPropertyKeys.addAll(properties);
      return objectMapper.writeValueAsString(apiPropertyKeys);
    }
  }

  private void updateResourcePropertiesExpectCode(
      UUID workspaceId, UUID resourceId, Map<String, String> newProperties, int code)
      throws Exception {
    updateResourcePropertiesExpectCode(
        workspaceId, resourceId, newProperties, userAccessUtils.defaultUserAuthRequest(), code);
  }

  private void updateResourcePropertiesExpectCode(
      UUID workspaceId,
      UUID resourceId,
      Map<String, String> newProperties,
      AuthenticatedUserRequest userRequest,
      int code)
      throws Exception {
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(RESOURCE_PROPERTIES_V1_PATH_FORMAT, workspaceId, resourceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(updateResourcePropertiesRequestInJson(newProperties)),
                    userRequest)))
        .andExpect(status().is(code));
  }

  private String updateResourcePropertiesRequestInJson(Map<String, String> properties)
      throws JsonProcessingException {
    ApiProperties apiProperties = convertMapToApiProperties(properties);
    return objectMapper.writeValueAsString(apiProperties);
  }
}
