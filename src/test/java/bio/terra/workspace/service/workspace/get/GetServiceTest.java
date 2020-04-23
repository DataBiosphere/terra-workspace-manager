package bio.terra.workspace.service.workspace.get;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.*;
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
public class GetServiceTest {

  @Autowired private MockMvc mvc;

  @MockBean private SamService mockSamService;

  // Mock MVC doesn't populate the fields used to build this.
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CreateService createService;

  @Autowired private GetService getService;

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
  public void testGetMissingWorkspace() throws Exception {
    MvcResult callResult =
        mvc.perform(get("/api/v1/workspaces/" + "fake-id")).andExpect(status().is(404)).andReturn();

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
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);

    assertThat(workspace.getId(), not(blankOrNullString()));

    MvcResult callResult =
        mvc.perform(get("/api/v1/workspaces/" + workspace.getId()))
            .andExpect(status().is(200))
            .andReturn();

    WorkspaceDescription desc =
        objectMapper.readValue(
            callResult.getResponse().getContentAsString(), WorkspaceDescription.class);

    assertThat(desc.getId(), equalTo(workspaceId));
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
}
