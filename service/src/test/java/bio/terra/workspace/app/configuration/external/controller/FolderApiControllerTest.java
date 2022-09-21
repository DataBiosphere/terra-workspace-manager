package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDERS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDER_PROPERTIES_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDER_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolder;
import bio.terra.workspace.generated.model.ApiFolderList;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiPropertyKeys;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

public class FolderApiControllerTest extends BaseUnitTest {
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;

  @MockBean SamService mockSamService;

  @BeforeEach
  public void setUp() throws InterruptedException {
    // Needed for workspace creation as logging is triggered when a workspace is created in
    // `WorkspaceActivityLogHook` where we extract the user request information and log it to
    // activity log.
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));

    // Needed for assertion that requester has role on workspace.
    when(mockSamService.listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
  }

  @Test
  public void createFolder_parentFolderIdIsNull_topLevelFolderCreated() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolder folder =
        createFolder(workspaceId, displayName, description, null, Map.of("foo", "bar"));

    assertEquals(displayName, folder.getDisplayName());
    assertEquals(description, folder.getDescription());
    assertEquals("bar", convertApiPropertyToMap(folder.getProperties()).get("foo"));
    assertEquals(1, folder.getProperties().size());
    assertNotNull(folder.getId());
    assertNull(folder.getParentFolderId());
  }

  @Test
  public void createFolder_doesNotHaveWriteAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.WRITE));

    createFolderExpectCode(workspaceId, HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void createFolder_parentFolderDoesNotExist_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    createFolderExpectCode(workspaceId, "foo", UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void createFolder_workspaceDoesNotExist_throws404() throws Exception {
    createFolderExpectCode(UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void createFolder_duplicateDisplayName_throws400() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";

    // create a top-level folder foo.
    ApiFolder firstFolder = createFolder(workspaceId);
    // create another top-level folder foo is not allowed
    createFolderExpectCode(workspaceId, HttpStatus.SC_BAD_REQUEST);

    // create a second level folder under foo.
    createFolderExpectCode(
        workspaceId,
        displayName,
        /*description=*/ null,
        firstFolder.getId(),
        /*properties=*/ Map.of(),
        HttpStatus.SC_OK);
    createFolderExpectCode(
        workspaceId,
        displayName,
        /*description=*/ null,
        firstFolder.getId(),
        /*properties=*/ Map.of(),
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void createFolder_duplicateNameUnderDifferentParent_succeeds() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder firstFolder = createFolder(workspaceId);
    ApiFolder secondFolder =
        createFolder(workspaceId, /*displayName=*/ "bar", /*parentFolderId=*/ null);

    var duplicateDisplayName = "foo";
    ApiFolder thirdFolder =
        createFolder(
            workspaceId,
            duplicateDisplayName,
            /*description=*/ null,
            /*parentFolderId=*/ firstFolder.getId(),
            /*properties=*/ Map.of());
    ApiFolder fourthFolder =
        createFolder(
            workspaceId,
            duplicateDisplayName,
            /*description=*/ null,
            /*parentFolderId=*/ secondFolder.getId(),
            /*properties=*/ Map.of());

    assertEquals(duplicateDisplayName, thirdFolder.getDisplayName());
    assertEquals(duplicateDisplayName, fourthFolder.getDisplayName());
  }

  @Test
  public void getFolder_returnsFolder() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder firstFolder = createFolder(workspaceId);

    ApiFolder retrievedFolder = getFolder(workspaceId, firstFolder.getId());

    assertEquals(firstFolder, retrievedFolder);
  }

  @Test
  public void getFolder_doesNotHaveReadAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.READ));

    getFolderExpectCode(workspaceId, folder.getId(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void getFolder_workspaceDoNotExist_throws404() throws Exception {
    getFolderExpectCode(
        /*workspaceId=*/ UUID.randomUUID(),
        /*folderId=*/ UUID.randomUUID(),
        HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void getFolder_folderDoNotExist_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    getFolderExpectCode(workspaceId, /*folderId=*/ UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listFolders_listAllFoldersInAWorkspace() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";

    ApiFolder firstFolder = createFolder(workspaceId, displayName, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(workspaceId, displayName, /*parentFolderId=*/ firstFolder.getId());

    ApiFolderList retrievedFolders = listFolders(workspaceId);

    var expectedFolders =
        new ApiFolderList().addFoldersItem(firstFolder).addFoldersItem(secondFolder);
    assertEquals(expectedFolders, retrievedFolders);
  }

  @Test
  public void listFolders_doesNotHaveReadAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    createFolderExpectCode(workspaceId, HttpStatus.SC_OK);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.READ));

    listFoldersExpectCode(workspaceId, HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void listFolders_workspaceNotFound_throws404() throws Exception {
    listFoldersExpectCode(UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteFolder_doesNotHaveWriteAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.WRITE));

    deleteFolderExpectCode(workspaceId, folder.getId(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void deleteFolders_workspaceAndFolderNotExist_throws404() throws Exception {
    deleteFolderExpectCode(UUID.randomUUID(), UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);

    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    deleteFolderExpectCode(workspaceId, UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteFolders_deleteTopLevelFolder_folderAndSubFoldersAllDeleted() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    ApiFolder firstFolder = createFolder(workspaceId);
    ApiFolder secondFolder =
        createFolder(workspaceId, /*displayName=*/ "foo", /*parentFolderId=*/ firstFolder.getId());
    ApiFolder thirdFolder =
        createFolder(workspaceId, /*displayName=*/ "foo", /*parentFolderId=*/ secondFolder.getId());

    deleteFolderExpectCode(workspaceId, firstFolder.getId(), HttpStatus.SC_NO_CONTENT);

    getFolderExpectCode(workspaceId, firstFolder.getId(), HttpStatus.SC_NOT_FOUND);
    getFolderExpectCode(workspaceId, secondFolder.getId(), HttpStatus.SC_NOT_FOUND);
    getFolderExpectCode(workspaceId, thirdFolder.getId(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void updateFolders_updateParentFalse_onlyUpdateNameAndDescription() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    ApiFolder firstFolder = createFolder(workspaceId, displayName, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(workspaceId, displayName, /*parentFolderId=*/ firstFolder.getId());

    var newDisplayName = "sofoo";
    var newDescription = "This is a very foo folder";
    ApiFolder updatedFolder =
        updateFolder(
            workspaceId,
            secondFolder.getId(),
            newDisplayName,
            newDescription,
            /*parentFolderId=*/ null,
            /*updateParent=*/ false);

    assertEquals(newDisplayName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription());
    assertEquals(secondFolder.getParentFolderId(), updatedFolder.getParentFolderId());
  }

  @Test
  public void updateFolders_updateParentTrue_folderMovedToTopLevel() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    ApiFolder firstFolder = createFolder(workspaceId, displayName, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(workspaceId, displayName, /*parentFolderId=*/ firstFolder.getId());

    ApiFolder updatedFolder =
        updateFolder(
            workspaceId,
            secondFolder.getId(),
            /*newDisplayName=*/ "bar", // There is already a top-level foo.
            /*newDescription=*/ null,
            /*parentFolderId=*/ null,
            /*updateParent=*/ true);

    assertNull(updatedFolder.getParentFolderId());
  }

  @Test
  public void updateFolder_doesNotHaveWriteAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.WRITE));

    updateFolderExpectCode(
        workspaceId,
        folder.getId(),
        /*newDisplayName=*/ null,
        /*newDescription=*/ null,
        /*parentFolderId=*/ null,
        /*updateParent=*/ true,
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void updateFolders_parentFolderNotExists_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);

    var newDisplayName = "sofoo";
    var newDescription = "This is a very foo folder";
    updateFolderExpectCode(
        workspaceId,
        folder.getId(),
        newDisplayName,
        newDescription,
        /*parentFolderId=*/ UUID.randomUUID(),
        /*updateParent=*/ false,
        HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void updateFolderProperties_success() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    // create folder with foo=bar
    ApiFolder folder = createFolder(workspaceId);

    // Add property cake=lava
    updateFolderPropertiesExpectCode(
        workspaceId,
        folder.getId(),
        Map.of("cake", "lava"),
        USER_REQUEST,
        HttpStatus.SC_NO_CONTENT);

    ApiFolder gotFolder = getFolder(workspaceId, folder.getId());
    Map<String, String> properties = convertApiPropertyToMap(gotFolder.getProperties());
    assertEquals("lava", properties.get("cake"));
    assertEquals("bar", properties.get("foo"));

    // update cake=lava to cake=chocolate
    updateFolderPropertiesExpectCode(
        workspaceId,
        folder.getId(),
        Map.of("cake", "chocolate"),
        USER_REQUEST,
        HttpStatus.SC_NO_CONTENT);

    ApiFolder gotFolder2 = getFolder(workspaceId, folder.getId());
    properties = convertApiPropertyToMap(gotFolder2.getProperties());
    assertEquals("chocolate", properties.get("cake"));
    assertEquals("bar", properties.get("foo"));
  }

  @Test
  public void updateFolderProperties_userHasNoWritePermission_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.WRITE));

    updateFolderPropertiesExpectCode(
        workspaceId, folder.getId(), Map.of("cake", "lava"), USER_REQUEST, HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void updateFolderProperties_folderNotExist_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    updateFolderPropertiesExpectCode(
        workspaceId,
        /*folderId=*/ UUID.randomUUID(),
        Map.of("cake", "lava"),
        USER_REQUEST,
        HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteFolderProperties_propertiesDeletedSuccessfully() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);

    assertTrue(convertApiPropertyToMap(folder.getProperties()).containsKey("foo"));
    deleteFolderPropertiesExpectCode(
        workspaceId, folder.getId(), List.of("foo"), USER_REQUEST, HttpStatus.SC_NO_CONTENT);

    ApiFolder gotFolder = getFolder(workspaceId, folder.getId());
    assertFalse(convertApiPropertyToMap(gotFolder.getProperties()).containsKey("foo"));
  }

  @Test
  public void deleteFolderProperties_folderNotFound_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    deleteFolderPropertiesExpectCode(
        workspaceId, UUID.randomUUID(), List.of("foo"), USER_REQUEST, HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteFolderProperties_userHasNoWritePermission_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder = createFolder(workspaceId);
    doThrow(new ForbiddenException("User has no write access"))
        .when(mockSamService)
        .checkAuthz(
            any(AuthenticatedUserRequest.class),
            eq(SamConstants.SamResource.WORKSPACE),
            anyString(),
            eq(SamConstants.SamWorkspaceAction.WRITE));

    deleteFolderPropertiesExpectCode(
        workspaceId, folder.getId(), List.of("foo"), USER_REQUEST, HttpStatus.SC_FORBIDDEN);
  }

  private ApiFolder createFolder(UUID workspaceId) throws Exception {
    return createFolder(
        workspaceId,
        /*displayName=*/ "foo",
        /*description=*/ null,
        /*parentFolderId=*/ null,
        Map.of("foo", "bar"));
  }

  private ApiFolder createFolder(UUID workspaceId, String displayName, UUID parentFolderId)
      throws Exception {
    return createFolder(
        workspaceId, displayName, /*description=*/ null, parentFolderId, Map.of("foo", "bar"));
  }

  private ApiFolder createFolder(
      UUID workspaceId,
      String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      Map<String, String> properties)
      throws Exception {
    String serializedResponse =
        createFolderExpectCode(
                workspaceId, displayName, description, parentFolderId, properties, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiFolder.class);
  }

  private ResultActions createFolderExpectCode(UUID workspaceId, int code) throws Exception {
    return createFolderExpectCode(
        workspaceId,
        /*displayName=*/ "foo",
        /*description=*/ null,
        /*parentFolderId=*/ null,
        Map.of("foo", "bar"),
        code);
  }

  private ResultActions createFolderExpectCode(
      UUID workspaceId, String displayName, UUID parentFolderId, int code) throws Exception {
    return createFolderExpectCode(
        workspaceId,
        displayName,
        /*description=*/ null,
        parentFolderId,
        Map.of("foo", "bar"),
        code);
  }

  /** Returns ResultActions because this is called by createFolder(). */
  private ResultActions createFolderExpectCode(
      UUID workspaceId,
      String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      Map<String, String> properties,
      int code)
      throws Exception {
    return mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                        .content(
                            objectMapper.writeValueAsString(
                                createFolderRequestBody(
                                    displayName, description, parentFolderId, properties))),
                    USER_REQUEST)))
        .andExpect(status().is(code));
  }

  private ApiCreateFolderRequestBody createFolderRequestBody(
      String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      Map<String, String> properties) {
    return new ApiCreateFolderRequestBody()
        .description(description)
        .displayName(displayName)
        .parentFolderId(parentFolderId)
        .properties(convertMapToApiProperties(properties));
  }

  private ApiFolder updateFolder(
      UUID workspaceId,
      UUID folderId,
      @Nullable String newDisplayName,
      @Nullable String newDescription,
      @Nullable UUID parentFolderId,
      boolean updateParent)
      throws Exception {
    String serializedResponse =
        updateFolderExpectCode(
                workspaceId,
                folderId,
                newDisplayName,
                newDescription,
                parentFolderId,
                updateParent,
                HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiFolder.class);
  }

  /** Returns ResultActions because this is called by updateFolder(). */
  private ResultActions updateFolderExpectCode(
      UUID workspaceId,
      UUID folderId,
      String newDisplayName,
      String newDescription,
      @Nullable UUID parentFolderId,
      boolean updateParent,
      int code)
      throws Exception {
    return mockMvc
        .perform(
            addAuth(
                patch(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, folderId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(
                        getUpdateRequestInJson(
                            newDisplayName, newDescription, parentFolderId, updateParent)),
                USER_REQUEST))
        .andExpect(status().is(code));
  }

  private String getUpdateRequestInJson(
      @Nullable String newDisplayName,
      @Nullable String newDescription,
      @Nullable UUID parentFolderId,
      boolean updateParent)
      throws JsonProcessingException {
    var requestBody =
        new ApiUpdateFolderRequestBody()
            .description(newDescription)
            .displayName(newDisplayName)
            .parentFolderId(parentFolderId)
            .updateParent(updateParent);
    return objectMapper.writeValueAsString(requestBody);
  }

  private ApiFolder getFolder(UUID workspaceId, UUID folderId) throws Exception {
    String serializedResponse =
        getFolderExpectCode(workspaceId, folderId, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiFolder.class);
  }

  /** Returns ResultActions because this is called by getFolder(). */
  private ResultActions getFolderExpectCode(UUID workspaceId, UUID folderId, int code)
      throws Exception {
    return mockMvc
        .perform(
            addAuth(get(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, folderId)), USER_REQUEST))
        .andExpect(status().is(code));
  }

  private ApiFolderList listFolders(UUID workspaceId) throws Exception {
    String serializedResponse =
        listFoldersExpectCode(workspaceId, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiFolderList.class);
  }

  /** Returns ResultActions because this is called by listFolder(). */
  private ResultActions listFoldersExpectCode(UUID workspaceId, int code) throws Exception {
    return mockMvc
        .perform(addAuth(get(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId)), USER_REQUEST))
        .andExpect(status().is(code));
  }

  private ResultActions deleteFolderExpectCode(UUID workspaceId, UUID folderId, int code)
      throws Exception {
    return mockMvc
        .perform(
            addAuth(
                delete(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, folderId)), USER_REQUEST))
        .andExpect(status().is(code));
  }

  private void updateFolderPropertiesExpectCode(
      UUID workspaceId,
      UUID folderId,
      Map<String, String> newProperties,
      AuthenticatedUserRequest userRequest,
      int code)
      throws Exception {
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDER_PROPERTIES_V1_PATH_FORMAT, workspaceId, folderId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(updateFolderPropertiesRequestInJson(newProperties)),
                    userRequest)))
        .andExpect(status().is(code));
  }

  private String updateFolderPropertiesRequestInJson(Map<String, String> properties)
      throws JsonProcessingException {
    ApiProperties apiProperties = convertMapToApiProperties(properties);
    return objectMapper.writeValueAsString(apiProperties);
  }

  private void deleteFolderPropertiesExpectCode(
      UUID workspaceId,
      UUID folderId,
      List<String> propertyKeysToDelete,
      AuthenticatedUserRequest userRequest,
      int code)
      throws Exception {
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    patch(String.format(FOLDER_PROPERTIES_V1_PATH_FORMAT, workspaceId, folderId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(getDeleteFolderPropertiesInJson(propertyKeysToDelete)),
                    userRequest)))
        .andExpect(status().is(code));
  }

  private String getDeleteFolderPropertiesInJson(List<String> properties)
      throws JsonProcessingException {
    ApiPropertyKeys apiPropertyKeys = new ApiPropertyKeys();
    apiPropertyKeys.addAll(properties);
    return objectMapper.writeValueAsString(apiPropertyKeys);
  }
}
