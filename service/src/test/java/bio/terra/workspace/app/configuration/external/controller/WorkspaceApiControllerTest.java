package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An example of a mockMvc-based unit test for a controller.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 */
@AutoConfigureMockMvc
public class WorkspaceApiControllerTest extends BaseConnectedTest {

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean SamService mockSamService;

  @BeforeEach
  public void setup() throws InterruptedException {
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                eq(SamResource.SPEND_PROFILE),
                Mockito.any(),
                eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    Mockito.when(mockSamService.listRequesterRoles(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
  }

  @Test
  public void getWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = createDefaultWorkspace();
    ApiWorkspaceDescription fetchedWorkspace = getWorkspaceDescription(workspace.getId());
    assertEquals(workspace.getId(), fetchedWorkspace.getId());
    assertNotNull(fetchedWorkspace.getLastUpdatedDate());
    assertEquals(fetchedWorkspace.getLastUpdatedDate(), fetchedWorkspace.getCreatedDate());
  }

  @Test
  public void getWorkspaceByUserFacingId() throws Exception {
    ApiCreatedWorkspace workspace = createDefaultWorkspace();
    String userFacingId = WorkspaceFixtures.getUserFacingId(workspace.getId());

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_BY_UFID_PATH_FORMAT, userFacingId)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription fetchedWorkspace =
        objectMapper.readValue(serializedGetResponse, ApiWorkspaceDescription.class);

    assertEquals(workspace.getId(), fetchedWorkspace.getId());
    assertEquals(userFacingId, fetchedWorkspace.getUserFacingId());
    assertNotNull(fetchedWorkspace.getLastUpdatedDate());
    assertEquals(fetchedWorkspace.getLastUpdatedDate(), fetchedWorkspace.getCreatedDate());
  }

  @Test
  public void updateWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = createDefaultWorkspace();
    String newDisplayName = "new workspace display name";
    String newUserFacingId = "new-ufid";
    String newDescription = "new description for the workspace";

    String serializedUpdateResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspace.getId()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            getUpdateRequestInJson(
                                newDisplayName, newUserFacingId, newDescription)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription updatedWorkspaceDescription =
        objectMapper.readValue(serializedUpdateResponse, ApiWorkspaceDescription.class);

    assertEquals(newDisplayName, updatedWorkspaceDescription.getDisplayName());
    assertEquals(newDescription, updatedWorkspaceDescription.getDescription());
    assertEquals(newUserFacingId, updatedWorkspaceDescription.getUserFacingId());
    OffsetDateTime firstLastUpdatedDate = updatedWorkspaceDescription.getLastUpdatedDate();
    assertNotNull(firstLastUpdatedDate);
    OffsetDateTime createdDate = updatedWorkspaceDescription.getCreatedDate();
    assertNotNull(createdDate);
    assertTrue(firstLastUpdatedDate.isAfter(createdDate));

    var secondNewDescription = "This is yet another description";
    String serializedSecondUpdateResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspace.getId()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            getUpdateRequestInJson(
                                newDisplayName, newUserFacingId, secondNewDescription)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription secondUpdatedWorkspaceDescription =
        objectMapper.readValue(serializedSecondUpdateResponse, ApiWorkspaceDescription.class);

    assertEquals(secondNewDescription, secondUpdatedWorkspaceDescription.getDescription());
    var secondLastUpdatedDate = secondUpdatedWorkspaceDescription.getLastUpdatedDate();
    assertTrue(firstLastUpdatedDate.isBefore(secondLastUpdatedDate));
    assertNotNull(secondUpdatedWorkspaceDescription.getCreatedDate());
    assertEquals(createdDate, secondUpdatedWorkspaceDescription.getCreatedDate());
  }

  private String getUpdateRequestInJson(
      String newDisplayName, String newUserFacingId, String newDescription)
      throws JsonProcessingException {
    var requestBody =
        new ApiUpdateWorkspaceRequestBody()
            .description(newDescription)
            .displayName(newDisplayName)
            .userFacingId(newUserFacingId);
    return objectMapper.writeValueAsString(requestBody);
  }

  @Test
  public void deleteWorkspaceProperties() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
    ApiWorkspaceDescription sourceWorkspace = getWorkspaceDescription(workspaceId);
    ArrayList propertyUpdate = new ArrayList<>(Arrays.asList("foo", "foo1"));

    mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(propertyUpdate)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT))
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertEquals(sourceWorkspace.getProperties(), buildProperties(Map.of("xyzzy", "plohg")));
  }

  private ApiCreatedWorkspace createDefaultWorkspace() throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  @Test
  public void cloneWorkspace() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
    ApiWorkspaceDescription sourceWorkspace = getWorkspaceDescription(workspaceId);

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(CLONE_WORKSPACE_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            objectMapper.writeValueAsString(
                                new ApiCloneWorkspaceRequest()
                                    .spendProfile(SamResource.SPEND_PROFILE))),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiCloneWorkspaceResult cloneWorkspace =
        objectMapper.readValue(serializedGetResponse, ApiCloneWorkspaceResult.class);

    UUID destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    ApiWorkspaceDescription destinationWorkspace = getWorkspaceDescription(destinationWorkspaceId);

    assertEquals(sourceWorkspace.getProperties(), destinationWorkspace.getProperties());
  }

  private ApiWorkspaceDescription getWorkspaceDescription(UUID id) throws Exception {
    String WorkspaceGetResponse =
        mockMvc
            .perform(
                addAuth(get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id)), USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription resultWorkspace =
        objectMapper.readValue(WorkspaceGetResponse, ApiWorkspaceDescription.class);

    return resultWorkspace;
  }

  public ApiProperties buildProperties(Map<String, String> propertyMap) {
    ApiProperties properties = new ApiProperties();
    ApiProperty property = new ApiProperty();

    for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
      property.setKey(entry.getKey());
      property.setValue(entry.getValue());
      properties.add(property);
    }

    return properties;
  }
}
