package bio.terra.workspace.service.workspace.create;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.ErrorReport;
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
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken("fake-user-auth-token")
            .spendProfile(null)
            .policies(null);

    CreatedWorkspace workspace = runCreateWorkspaceCall(body);

    assertThat(workspace.getId(), equalTo(workspaceId.toString()));
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

    assertThat(workspace.getId(), equalTo(workspaceId.toString()));
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
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(500))
            .andReturn();

    ErrorReport samError =
        objectMapper.readValue(callResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(samError.getMessage(), equalTo(errorMsg));
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
