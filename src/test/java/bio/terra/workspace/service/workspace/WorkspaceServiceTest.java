package bio.terra.workspace.service.workspace;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.DeleteWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class WorkspaceServiceTest {

  @Autowired private MockMvc mvc;

  @MockBean private SamService mockSamService;

  // Mock MVC doesn't populate the fields used to build this.
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;

  @MockBean private DataRepoService dataRepoService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WorkspaceService workspaceService;

  @BeforeEach
  public void setup() {
    doNothing().when(mockSamService).createWorkspaceWithDefaults(any(), any());
    doNothing().when(mockSamService).workspaceAuthz(any(), any(), any());
    doReturn(true).when(dataRepoService).snapshotExists(any(), any(), any());
    AuthenticatedUserRequest fakeAuthentication = new AuthenticatedUserRequest();
    fakeAuthentication
        .token(Optional.of("fake-token"))
        .email("fake@email.com")
        .subjectId("fakeID123");
    when(mockAuthenticatedUserRequestFactory.from(any())).thenReturn(fakeAuthentication);
  }

  @Test
  public void testGetMissingWorkspace() throws Exception {
    MvcResult callResult =
        mvc.perform(get("/api/workspaces/v1/" + UUID.randomUUID().toString()))
            .andExpect(status().is(404))
            .andReturn();

    ErrorReport error =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(error.getStatusCode(), equalTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  public void testGetExistingWorkspace() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    UUID workspaceId = UUID.randomUUID();
    body.setId(workspaceId);
    body.setAuthToken("fake-user-auth-token");

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);

    assertThat(workspace.getId().toString(), not(blankOrNullString()));

    MvcResult callResult =
        mvc.perform(get("/api/workspaces/v1/" + workspace.getId()))
            .andExpect(status().is(200))
            .andReturn();

    WorkspaceDescription desc =
        objectMapper.readValue(
            callResult.getResponse().getContentAsString(), WorkspaceDescription.class);

    assertThat(desc.getId(), equalTo(workspaceId));
  }

  @Test
  public void workspaceCreatedFromJobRequest() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);

    assertThat(workspace.getId(), equalTo(workspaceId));
  }

  @Test
  public void duplicateWorkspaceRejected() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);
    CreatedWorkspace workspace = runCreateWorkspaceCall(body);
    assertThat(workspace.getId(), equalTo(workspaceId));

    MvcResult failureResult =
        mvc.perform(
                post("/api/workspaces/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(400))
            .andReturn();
    ErrorReport error =
        objectMapper.readValue(failureResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(error.getMessage(), containsString("already exists"));
  }

  @Test
  public void testWithSpendProfileAndPolicies() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(UUID.randomUUID())
            .policies(Collections.singletonList(UUID.randomUUID()));

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);

    assertThat(workspace.getId(), equalTo(workspaceId));
  }

  @Test
  public void testHandlesSamError() throws Exception {
    String errorMsg = "fake SAM error message";

    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(UUID.randomUUID())
            .authToken("todo: add token")
            .spendProfile(null)
            .policies(null);

    MvcResult callResult =
        mvc.perform(
                post("/api/workspaces/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(500))
            .andReturn();

    ErrorReport samError =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(samError.getMessage(), equalTo(errorMsg));
  }

  @Test
  public void createAndDeleteWorkspace() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);
    assertThat(workspace.getId(), equalTo(workspaceId));

    DeleteWorkspaceRequestBody deleteBody =
        new DeleteWorkspaceRequestBody().authToken("fake-user-auth-token");
    MvcResult deleteResult =
        mvc.perform(
                delete("/api/workspaces/v1/" + workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(deleteBody)))
            .andExpect(status().is(204))
            .andReturn();
    // Finally, assert that a call to the deleted workspace gives a 404
    MvcResult getResult =
        mvc.perform(get("/api/workspaces/v1/" + workspaceId))
            .andExpect(status().is(404))
            .andReturn();
  }

  @Test
  public void deleteWorkspaceWithDataReference() throws Exception {
    // First, create a workspace.
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);
    CreatedWorkspace workspace = runCreateWorkspaceCall(body);
    assertThat(workspace.getId(), equalTo(workspaceId));

    // Next, add a data reference to that workspace.
    DataRepoSnapshot reference =
        new DataRepoSnapshot().instanceName("fake instance").snapshot("fake snapshot");
    CreateDataReferenceRequestBody referenceRequest =
        new CreateDataReferenceRequestBody()
            .name("fake-data-reference")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(reference));
    MvcResult dataReferenceResult =
        mvc.perform(
                post("/api/workspaces/v1/" + workspaceId + "/datareferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(referenceRequest)))
            .andExpect(status().is(200))
            .andReturn();
    DataReferenceDescription dataReferenceResponse =
        objectMapper.readValue(
            dataReferenceResult.getResponse().getContentAsString(), DataReferenceDescription.class);
    // Validate that the reference exists.
    MvcResult getReferenceResult =
        mvc.perform(
                get("/api/workspaces/v1/"
                        + workspaceId
                        + "/datareferences/"
                        + dataReferenceResponse.getReferenceId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(referenceRequest)))
            .andExpect(status().is(200))
            .andReturn();
    // Delete the workspace.
    DeleteWorkspaceRequestBody deleteRequest =
        new DeleteWorkspaceRequestBody().authToken("fake-user-auth-token");
    MvcResult deleteResult =
        mvc.perform(
                delete("/api/workspaces/v1/" + workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(deleteRequest)))
            .andExpect(status().is(204))
            .andReturn();
    // Verify that the contained data reference is no longer returned.
    MvcResult getDeletedReferenceResult =
        mvc.perform(
                get("/api/workspaces/v1/"
                        + workspaceId
                        + "/datareferences/"
                        + dataReferenceResponse.getReferenceId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(referenceRequest)))
            .andExpect(status().is(404))
            .andReturn();
  }

  // TODO: blank tests that should be written as more functionality gets added.
  // @Test
  // public void testLockedWorkspaceIsInaccessible() {
  // }
  // @Test
  // public void testCreateFromNonFolderManagerIsRejected() {
  // }
  // @Test
  // public void testPolicy() {
  // }

  private CreatedWorkspace runCreateWorkspaceCall(CreateWorkspaceRequestBody request)
      throws Exception {
    MvcResult initialResult =
        mvc.perform(
                post("/api/workspaces/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), CreatedWorkspace.class);
  }
}
