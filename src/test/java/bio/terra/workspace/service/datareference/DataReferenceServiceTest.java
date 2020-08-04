package bio.terra.workspace.service.datareference;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
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

  @Autowired private WorkspaceService workspaceService;

  @Autowired private DataReferenceService dataReferenceService;

  private UUID workspaceId;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    doNothing().when(mockSamService).workspaceAuthz(any(), any(), any());
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
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));

    DataReferenceDescription response = runCreateDataReferenceCall(initialWorkspaceId, refBody);

    assertThat(response.getWorkspaceId(), equalTo(initialWorkspaceId));
    assertThat(response.getName(), equalTo("name"));
  }

  @Test
  public void testGetDataReference() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));

    DataReferenceDescription createResponse =
        runCreateDataReferenceCall(initialWorkspaceId, refBody);

    String referenceId = createResponse.getReferenceId().toString();

    DataReferenceDescription getResponse = runGetDataReferenceCall(initialWorkspaceId, referenceId);

    assertThat(getResponse.getWorkspaceId(), equalTo(initialWorkspaceId));
    assertThat(getResponse.getName(), equalTo("name"));
  }

  @Test
  public void testGetDataReferenceByName() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));

    DataReferenceDescription createResponse =
        runCreateDataReferenceCall(initialWorkspaceId, refBody);

    DataReferenceDescription getResponse =
        runGetDataReferenceByNameCall(
            initialWorkspaceId, ReferenceTypeEnum.DATA_REPO_SNAPSHOT, "name");

    assertThat(getResponse.getWorkspaceId(), equalTo(initialWorkspaceId));
    assertThat(getResponse.getName(), equalTo("name"));
  }

  @Test
  public void testGetMissingDataReference() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    MvcResult callResult =
        mvc.perform(
                get(
                    "/api/workspaces/v1/"
                        + initialWorkspaceId.toString()
                        + "/datareferences/"
                        + UUID.randomUUID().toString()))
            .andExpect(status().is(404))
            .andReturn();

    ErrorReport error =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(error.getStatusCode(), equalTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  public void testCreateDataSnapshotNotInDataRepo() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("fake-id");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));

    mvc.perform(
            post("/api/workspaces/v1/" + initialWorkspaceId.toString() + "/datareferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refBody)))
        .andExpect(status().is(400))
        .andReturn();
  }

  @Test
  public void testCreateInvalidDataReference() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference("bad-reference");

    mvc.perform(
            post("/api/workspaces/v1/" + initialWorkspaceId.toString() + "/datareferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refBody)))
        .andExpect(status().is(400))
        .andReturn();
  }

  @Test
  public void enumerateDataReferences() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));
    DataReferenceDescription firstReference =
        runCreateDataReferenceCall(initialWorkspaceId, refBody);

    DataRepoSnapshot secondSnapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo2");
    snapshot.setInstanceName("bar2");
    CreateDataReferenceRequestBody secondRefBody =
        new CreateDataReferenceRequestBody()
            .name("second_name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(secondSnapshot));
    DataReferenceDescription secondReference =
        runCreateDataReferenceCall(initialWorkspaceId, secondRefBody);

    MvcResult enumerateResult =
        mvc.perform(get(buildEnumerateEndpoint(initialWorkspaceId, 0, 10)))
            .andExpect(status().is(200))
            .andReturn();
    DataReferenceList result =
        objectMapper.readValue(
            enumerateResult.getResponse().getContentAsString(), DataReferenceList.class);
    assertThat(result.getResources().size(), equalTo(2));
    assertThat(
        result.getResources(),
        containsInAnyOrder(equalTo(firstReference), equalTo(secondReference)));
  }

  @Test
  public void enumerateFailsUnauthorized() throws Exception {
    String samMessage = "Fake Sam unauthorized message";
    doThrow(new SamUnauthorizedException(samMessage))
        .when(mockSamService)
        .workspaceAuthz(any(), any(), any());
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId, 0, 10)))
            .andExpect(status().is(401))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getMessage(), containsString(samMessage));
  }

  @Test
  public void enumerateFailsWithInvalidOffset() throws Exception {
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId, -1, 10)))
            .andExpect(status().is(400))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getCauses().get(0), containsString("offset"));
  }

  @Test
  public void enumerateFailsWithInvalidLimit() throws Exception {
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId, 0, 0)))
            .andExpect(status().is(400))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getCauses().get(0), containsString("limit"));
  }

  private String buildEnumerateEndpoint(UUID workspaceId, int offset, int limit) {
    return "/api/workspaces/v1/"
        + workspaceId.toString()
        + "/datareferences?offset="
        + offset
        + "&limit="
        + limit;
  }

  public void testDeleteDataReference() throws Exception {
    UUID initialWorkspaceId = createDefaultWorkspace().getId();

    DataRepoSnapshot snapshot = new DataRepoSnapshot();
    snapshot.setSnapshot("foo");
    snapshot.setInstanceName("bar");

    CreateDataReferenceRequestBody refBody =
        new CreateDataReferenceRequestBody()
            .name("name")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(objectMapper.writeValueAsString(snapshot));

    DataReferenceDescription response = runCreateDataReferenceCall(initialWorkspaceId, refBody);
    DataReferenceDescription getResponse =
        runGetDataReferenceCall(initialWorkspaceId, response.getReferenceId().toString());

    assertThat(getResponse.getName(), equalTo("name"));

    runDeleteDataReferenceCall(initialWorkspaceId, response.getReferenceId().toString());

    // assert that reference is now deleted
    mvc.perform(
            get("/api/workspaces/v1/"
                    + initialWorkspaceId.toString()
                    + "/datareferences/"
                    + response.getReferenceId().toString())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is(404))
        .andReturn();
  }

  @Test
  public void testDeleteMissingDataReference() throws Exception {
    MvcResult callResult =
        mvc.perform(
                delete(
                    "/api/workspaces/v1/"
                        + workspaceId.toString()
                        + "/datareferences/"
                        + UUID.randomUUID().toString()))
            .andExpect(status().is(404))
            .andReturn();

    ErrorReport error =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(error.getStatusCode(), equalTo(HttpStatus.NOT_FOUND.value()));
  }

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

  private DataReferenceDescription runCreateDataReferenceCall(
      UUID workspaceId, CreateDataReferenceRequestBody request) throws Exception {
    MvcResult initialResult =
        mvc.perform(
                post("/api/workspaces/v1/" + workspaceId.toString() + "/datareferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), DataReferenceDescription.class);
  }

  private DataReferenceDescription runGetDataReferenceCall(UUID workspaceId, String referenceId)
      throws Exception {
    MvcResult initialResult =
        mvc.perform(
                get("/api/workspaces/v1/"
                        + workspaceId.toString()
                        + "/datareferences/"
                        + referenceId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), DataReferenceDescription.class);
  }

  private DataReferenceDescription runGetDataReferenceByNameCall(
      UUID workspaceId, ReferenceTypeEnum referenceType, String name) throws Exception {
    MvcResult initialResult =
        mvc.perform(
                get("/api/workspaces/v1/"
                        + workspaceId.toString()
                        + "/datareferences/"
                        + referenceType.toString()
                        + "/"
                        + name)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(200))
            .andReturn();
    return objectMapper.readValue(
        initialResult.getResponse().getContentAsString(), DataReferenceDescription.class);
  }

  private void runDeleteDataReferenceCall(UUID workspaceId, String referenceId) throws Exception {
    mvc.perform(
            delete(
                    "/api/workspaces/v1/"
                        + workspaceId.toString()
                        + "/datareferences/"
                        + referenceId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is(204))
        .andReturn();
  }

  private CreatedWorkspace createDefaultWorkspace() throws Exception {
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);

    return runCreateWorkspaceCall(body);
  }
}
