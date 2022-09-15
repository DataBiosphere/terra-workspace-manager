package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDERS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDER_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

    ApiFolder folder = createFolder(workspaceId, displayName, description, null);

    assertEquals(displayName, folder.getDisplayName());
    assertEquals(description, folder.getDescription());
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

    createFolderExpectCode(workspaceId, "foo", HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void createFolder_parentFolderDoesNotExist_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    createFolderExpectCode(
        workspaceId, "foo", /*description=*/ null, UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void createFolder_workspaceDoesNotExist_throws404() throws Exception {
    createFolderExpectCode(UUID.randomUUID(), "foo", HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void createFolder_duplicateDisplayName_throws400() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";

    // create a top-level folder foo.
    ApiFolder firstFolder =
        createFolder(workspaceId, displayName, /*description=*/ null, /*parentFolderId=*/ null);
    // create another top-level folder foo is not allowed
    createFolderExpectCode(workspaceId, displayName, HttpStatus.SC_BAD_REQUEST);

    // create a second level folder under foo.
    createFolderExpectCode(
        workspaceId, displayName, /*description=*/ null, firstFolder.getId(), HttpStatus.SC_OK);
    createFolderExpectCode(
        workspaceId,
        displayName,
        /*description=*/ null,
        firstFolder.getId(),
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void createFolder_duplicateNameUnderDifferentParent_succeeds() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder firstFolder = createFolder(workspaceId, /*displayName=*/ "foo");
    ApiFolder secondFolder = createFolder(workspaceId, /*displayName=*/ "bar");

    var duplicateDisplayName = "foo";
    ApiFolder thirdFolder =
        createFolder(
            workspaceId,
            duplicateDisplayName,
            /*description=*/ null,
            /*parentFolderId=*/ firstFolder.getId());
    ApiFolder fourthFolder =
        createFolder(
            workspaceId,
            duplicateDisplayName,
            /*description=*/ null,
            /*parentFolderId=*/ secondFolder.getId());

    assertEquals(duplicateDisplayName, thirdFolder.getDisplayName());
    assertEquals(duplicateDisplayName, fourthFolder.getDisplayName());
  }

  @Test
  public void getFolder_returnsFolder() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);
    ApiFolder firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);

    ApiFolder retrievedFolder = getFolder(workspaceId, firstFolder.getId());

    assertEquals(firstFolder, retrievedFolder);
  }

  @Test
  public void getFolder_doesNotHaveReadAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiFolder folder =
        createFolder(
            workspaceId, /*displayName=*/ "foo", /*description=*/ null, /*parentFolderId=*/ null);
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
    var description = String.format("This is folder %s", displayName);

    ApiFolder firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(
            workspaceId, displayName, description, /*parentFolderId=*/ firstFolder.getId());

    ApiFolderList retrievedFolders = listFolders(workspaceId);

    var expectedFolders =
        new ApiFolderList().addFoldersItem(firstFolder).addFoldersItem(secondFolder);
    assertEquals(expectedFolders, retrievedFolders);
  }

  @Test
  public void listFolders_doesNotHaveReadAccess_throws403() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    createFolderExpectCode(workspaceId, /*displayName=*/ "foo", HttpStatus.SC_OK);
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
    ApiFolder folder = createFolder(workspaceId, /*displayName=*/ "foo");
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

    ApiFolder firstFolder = createFolder(workspaceId, /*displayName=*/ "foo");
    ApiFolder secondFolder =
        createFolder(
            workspaceId,
            /*displayName=*/ "foo", /*description*/
            null,
            /*parentFolderId=*/ firstFolder.getId());
    ApiFolder thirdFolder =
        createFolder(
            workspaceId,
            /*displayName=*/ "foo", /*description*/
            null,
            /*parentFolderId=*/ secondFolder.getId());

    deleteFolderExpectCode(workspaceId, firstFolder.getId(), HttpStatus.SC_NO_CONTENT);

    getFolderExpectCode(workspaceId, firstFolder.getId(), HttpStatus.SC_NOT_FOUND);
    getFolderExpectCode(workspaceId, secondFolder.getId(), HttpStatus.SC_NOT_FOUND);
    getFolderExpectCode(workspaceId, thirdFolder.getId(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void updateFolders_updateParentFalse_onlyUpdateNameAndDescription() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);
    ApiFolder firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(
            workspaceId, displayName, description, /*parentFolderId=*/ firstFolder.getId());

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
    var description = String.format("This is folder %s", displayName);
    ApiFolder firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolder secondFolder =
        createFolder(
            workspaceId, displayName, description, /*parentFolderId=*/ firstFolder.getId());

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
    ApiFolder folder = createFolder(workspaceId, /*displayName=*/ "foo");
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
        true,
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void updateFolders_parentFolderNotExists_throws404() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolder folder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);

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

  private ApiFolder createFolder(UUID workspaceId, String displayName) throws Exception {
    return createFolder(workspaceId, displayName, /*description=*/ null, /*parentFolderId=*/ null);
  }

  private ApiFolder createFolder(
      UUID workspaceId,
      String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId)
      throws Exception {
    String serializedResponse =
        createFolderExpectCode(
                workspaceId, displayName, description, parentFolderId, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiFolder.class);
  }

  private ResultActions createFolderExpectCode(UUID workspaceId, String displayName, int code)
      throws Exception {
    return createFolderExpectCode(
        workspaceId, displayName, /*description=*/ null, /*parentFolderId=*/ null, code);
  }

  /** Returns ResultActions because this is called by createFolder(). */
  private ResultActions createFolderExpectCode(
      UUID workspaceId,
      String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      int code)
      throws Exception {
    return mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                        .content(
                            objectMapper.writeValueAsString(
                                createFolderRequestBody(displayName, description, parentFolderId))),
                    USER_REQUEST)))
        .andExpect(status().is(code));
  }

  private ApiCreateFolderRequestBody createFolderRequestBody(
      String displayName, @Nullable String description, @Nullable UUID parentFolderId) {
    return new ApiCreateFolderRequestBody()
        .description(description)
        .displayName(displayName)
        .parentFolderId(parentFolderId);
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
}
