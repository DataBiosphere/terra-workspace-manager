package bio.terra.workspace.service.create;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.generated.model.JobControl;
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
import org.openapitools.jackson.nullable.JsonNullable;
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
public class CreateServiceTest {

  @Autowired private MockMvc mvc;

  @MockBean private SamService mockSamService;

  // Mock MVC doesn't populate the fields used to build authenticated requests.
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CreateService createService;

  @BeforeEach
  public void setup() {
    doNothing().when(mockSamService).createWorkspaceWithDefaults(any(), any());
    AuthenticatedUserRequest fakeAuthentication = new AuthenticatedUserRequest();
    fakeAuthentication
        .token(Optional.of("fake-token"))
        .email("fake@email.com")
        .subjectId("fakeID123");
    when(mockAuthenticatedUserRequestFactory.from(any())).thenReturn(fakeAuthentication);
  }

  @Test
  public void workspaceCreatedFromJobRequest() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("fake-user-auth-token");
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());
    JobControl jobControl = new JobControl();
    String jobId = UUID.randomUUID().toString();
    jobControl.setJobid(jobId);
    body.setJobControl(jobControl);

    CreatedWorkspace workspace = runCreateWorkspaceCall(body, jobId);

    assertThat(workspace.getId(), not(blankOrNullString()));
  }

  @Test
  public void testWithSpendProfileAndPolicies() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.of(UUID.randomUUID()));
    body.setPolicies(JsonNullable.of(Collections.singletonList(UUID.randomUUID())));
    JobControl jobControl = new JobControl();
    String jobId = UUID.randomUUID().toString();
    jobControl.setJobid(jobId);
    body.setJobControl(jobControl);

    CreatedWorkspace workspace = runCreateWorkspaceCall(body, jobId);

    assertThat(workspace.getId(), not(blankOrNullString()));
  }

  @Test
  public void testUnauthorizedUserIsRejected() throws Exception {
    String errorMsg = "fake SAM error message";

    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());
    JobControl jobControl = new JobControl();
    String jobId = UUID.randomUUID().toString();
    jobControl.setJobid(jobId);
    body.setJobControl(jobControl);

    MvcResult createResponse = callCreateEndpoint(body);
    pollJobUntilComplete(jobId);

    MvcResult callResult =
        mvc.perform(get("/api/v1/jobs/" + jobId + "/result"))
            .andExpect(status().is(500))
            .andReturn();

    ErrorReport samError =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(samError.getMessage(), equalTo(errorMsg));
  }

  private CreatedWorkspace runCreateWorkspaceCall(CreateWorkspaceRequestBody request, String jobId)
      throws Exception {
    MvcResult initialResult = callCreateEndpoint(request);
    pollJobUntilComplete(jobId);
    return getCreateJobResult(jobId);
  }

  private MvcResult callCreateEndpoint(CreateWorkspaceRequestBody request) throws Exception {
    return mvc.perform(
            post("/api/v1/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().is(202))
        .andReturn();
  }

  private void pollJobUntilComplete(String jobId) throws Exception {
    HttpStatus pollStatus = HttpStatus.valueOf(202);
    while (pollStatus == HttpStatus.valueOf(202)) {
      MvcResult pollResult = mvc.perform(get("/api/v1/jobs/" + jobId)).andReturn();
      pollStatus = HttpStatus.valueOf(pollResult.getResponse().getStatus());
    }
    assertThat(pollStatus, equalTo(HttpStatus.OK));
  }

  private CreatedWorkspace getCreateJobResult(String jobId) throws Exception {
    MvcResult callResult =
        mvc.perform(get("/api/v1/jobs/" + jobId + "/result"))
            .andExpect(status().is(200))
            .andReturn();

    return objectMapper.readValue(
        callResult.getResponse().getContentAsString(), CreatedWorkspace.class);
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

}
