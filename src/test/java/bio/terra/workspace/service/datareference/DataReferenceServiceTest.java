package bio.terra.workspace.service.datareference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.create.CreateService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DataReferenceServiceTest {

  @Autowired private MockMvc mvc;

  @MockBean private SamService mockSamService;

  // Mock MVC doesn't populate the fields used to build this.
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;

  @MockBean private DataRepoService mockDataRepoService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CreateService createService;

  @Autowired private DataReferenceService dataReferenceService;

  @BeforeEach
  public void setup() {
    doNothing().when(mockSamService).createWorkspaceWithDefaults(any(), any());
    doReturn(true).when(mockDataRepoService).snapshotExists(any(), any(), any());
    doReturn(false).when(mockDataRepoService).snapshotExists(any(), eq("fake-id"), any());
    AuthenticatedUserRequest fakeAuthentication = new AuthenticatedUserRequest();
    fakeAuthentication
        .token(Optional.of("fake-token"))
        .email("fake@email.com")
        .subjectId("fakeID123");
    when(mockAuthenticatedUserRequestFactory.from(any())).thenReturn(fakeAuthentication);
  }

  @Test
  public void testCreateDataReference() throws Exception {
    String initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstance("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions("COPY_NOTHING")
            .referenceType("DataRepoSnapshot")
            .reference(snapshot);

    DataReferenceDescription response = runCreateDataReferenceCall(initialWorkspaceId, refBody);

    assertThat(response.getWorkspaceId(), equalTo(initialWorkspaceId));
    assertThat(response.getName(), equalTo("name"));
  }

  @Test
  public void testGetDataReference() throws Exception {
    String initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstance("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions("COPY_NOTHING")
            .referenceType("DataRepoSnapshot")
            .reference(snapshot);

    DataReferenceDescription createResponse =
        runCreateDataReferenceCall(initialWorkspaceId.toString(), refBody);

    String referenceId = createResponse.getReferenceId().toString();

    DataReferenceDescription getResponse = runGetDataReferenceCall(initialWorkspaceId, referenceId);

    assertThat(getResponse.getWorkspaceId(), equalTo(initialWorkspaceId));
    assertThat(getResponse.getName(), equalTo("name"));
  }

  @Test
  public void testGetMissingDataReference() throws Exception {
    MvcResult callResult =
        mvc.perform(
                get(
                    "/api/v1/workspaces/fake-workspace/datareferences/"
                        + UUID.randomUUID().toString()))
            .andExpect(status().is(404))
            .andReturn();

    ErrorReport error =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(error.getStatusCode(), equalTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  public void testCreateDataSnapshotNotInDataRepo() throws Exception {
    String initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("fake-id");
    snapshot.setInstance("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions("COPY_NOTHING")
            .referenceType("DataRepoSnapshot")
            .reference(snapshot);

    mvc.perform(
            post("/api/v1/workspaces/" + initialWorkspaceId + "/datareferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refBody)))
        .andExpect(status().is(400))
        .andReturn();
  }

  @Test
  public void testCreateInvalidDataReference() throws Exception {
    String initialWorkspaceId = createDefaultWorkspace().getId();

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions("COPY_NOTHING")
            .referenceType("DataRepoSnapshot")
            .reference("bad-reference");

    mvc.perform(
            post("/api/v1/workspaces/" + initialWorkspaceId + "/datareferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refBody)))
        .andExpect(status().is(400))
        .andReturn();
  }

  private CreatedWorkspace runCreateWorkspaceCall(CreateWorkspaceRequestBody request)
      throws Exception {
    MvcResult initialResult =
        mvc.perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), CreatedWorkspace.class);
  }

  private DataReferenceDescription runCreateDataReferenceCall(
      String workspaceId, CreateDataReferenceRequestBody request) throws Exception {
    MvcResult initialResult =
        mvc.perform(
                post("/api/v1/workspaces/" + workspaceId + "/datareferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), DataReferenceDescription.class);
  }

  private DataReferenceDescription runGetDataReferenceCall(String workspaceId, String referenceId)
      throws Exception {
    MvcResult initialResult =
        mvc.perform(
                get("/api/v1/workspaces/" + workspaceId + "/datareferences/" + referenceId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), DataReferenceDescription.class);
  }

  private CreatedWorkspace createDefaultWorkspace() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);

    return runCreateWorkspaceCall(body);
  }
}
