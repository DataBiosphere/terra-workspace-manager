package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.createDefaultWorkspace;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDERS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.FOLDER_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolderDescription;
import bio.terra.workspace.generated.model.ApiFolderDescriptionsList;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class FolderApiControllerTest extends BaseUnitTest {
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean SamService mockSamService;

  @BeforeEach
  public void setUp() throws InterruptedException {
    // Needed for workspace creation as logging is triggered when a workspace is created in
    // `WorkspaceActivityLogHook` where we extract the user request information and log it to
    // change agents.
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
  }

  @Test
  public void createFolder_topLevelFolderCreated() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription folderDescription =
        createFolder(workspaceId, displayName, description, null);

    assertEquals(displayName, folderDescription.getDisplayName());
    assertEquals(description, folderDescription.getDescription());
    assertNotNull(folderDescription.getId());
    assertNull(folderDescription.getParentFolderId());
  }

  @Test
  public void createFolder_parentFolderDoesNotExist_throws404() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                        .content(
                            objectMapper.writeValueAsString(
                                createFolderRequestBody(
                                    displayName, description, UUID.randomUUID()))),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  public void createFolder_duplicateFolder_throws400() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                        .content(
                            objectMapper.writeValueAsString(
                                createFolderRequestBody(displayName, description, null))),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK));

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                        .content(
                            objectMapper.writeValueAsString(
                                createFolderRequestBody(displayName, description, null))),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createFolder_subFolder() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolderDescription secondFolder =
        createFolder(workspaceId, displayName, description, firstFolder.getId());

    assertEquals(displayName, secondFolder.getDisplayName());
    assertEquals(description, secondFolder.getDescription());
    assertEquals(firstFolder.getId(), secondFolder.getParentFolderId());
    assertNotNull(secondFolder.getId());
  }

  @Test
  public void getFolder_returnsFolderDescription() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);

    String folderGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, firstFolder.getId())),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiFolderDescription retrievedFolder =
        objectMapper.readValue(folderGetResponse, ApiFolderDescription.class);

    assertEquals(firstFolder, retrievedFolder);
  }

  @Test
  public void getFolder_invalidFolder_throws404() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();

    mockMvc
        .perform(
            addAuth(
                get(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, UUID.randomUUID())),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  public void listFolders_listAllFoldersInAWorkspace() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolderDescription secondFolder =
        createFolder(
            workspaceId, displayName, description, /*parentFolderId=*/ firstFolder.getId());

    String foldersGetResponse =
        mockMvc
            .perform(addAuth(get(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId)), USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var retrievedFolders =
        objectMapper.readValue(foldersGetResponse, ApiFolderDescriptionsList.class);

    var expectedFolders =
        new ApiFolderDescriptionsList().addFoldersItem(firstFolder).addFoldersItem(secondFolder);
    assertEquals(expectedFolders, retrievedFolders);
  }

  @Test
  public void updateFolders_folderUpdated() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);
    ApiFolderDescription secondFolder =
        createFolder(
            workspaceId, displayName, description, /*parentFolderId=*/ firstFolder.getId());

    var newDisplayName = "sofoo";
    var newDescription = "This is a very foo folder";
    String serializedUpdateResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, secondFolder.getId()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            getUpdateRequestInJson(newDisplayName, newDescription, null, true)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var updatedFolderDescription =
        objectMapper.readValue(serializedUpdateResponse, ApiFolderDescription.class);

    assertEquals(newDisplayName, updatedFolderDescription.getDisplayName());
    assertEquals(newDescription, updatedFolderDescription.getDescription());
    assertNull(updatedFolderDescription.getParentFolderId());
  }

  @Test
  public void updateFolders_invalidParentFolder_throws404() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
    var displayName = "foo";
    var description = String.format("This is folder %s", displayName);

    ApiFolderDescription firstFolder =
        createFolder(workspaceId, displayName, description, /*parentFolderId=*/ null);

    var newDisplayName = "sofoo";
    var newDescription = "This is a very foo folder";
    mockMvc
        .perform(
            addAuth(
                patch(String.format(FOLDER_V1_PATH_FORMAT, workspaceId, firstFolder.getId()))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(
                        getUpdateRequestInJson(
                            newDisplayName, newDescription, UUID.randomUUID(), false)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  private ApiFolderDescription createFolder(
      UUID workspaceId, String displayName, String description, @Nullable UUID parentFolderId)
      throws Exception {
    String createResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(String.format(FOLDERS_V1_PATH_FORMAT, workspaceId))
                            .content(
                                objectMapper.writeValueAsString(
                                    createFolderRequestBody(
                                        displayName, description, parentFolderId))),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(createResponse, ApiFolderDescription.class);
  }

  private ApiCreateFolderRequestBody createFolderRequestBody(
      String displayName, @Nullable String description, @Nullable UUID parentFolderId) {
    return new ApiCreateFolderRequestBody()
        .description(description)
        .displayName(displayName)
        .parentFolderId(parentFolderId);
  }

  private String getUpdateRequestInJson(
      String newDisplayName,
      @Nullable String newDescription,
      @Nullable UUID parentFolderId,
      boolean moveToTop)
      throws JsonProcessingException {
    var requestBody =
        new ApiUpdateFolderRequestBody()
            .description(newDescription)
            .displayName(newDisplayName)
            .parentFolderId(parentFolderId)
            .moveToTopLevel(moveToTop);
    return objectMapper.writeValueAsString(requestBody);
  }
}
