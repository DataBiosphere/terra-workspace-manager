package bio.terra.workspace.service.datareference;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
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

  @Autowired private DataReferenceService dataReferenceService;

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  // Mock MVC doesn't populate the fields used to build this.
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;
  private AuthenticatedUserRequest fakeUserReq;

  @MockBean private SamService mockSamService;

  private UUID workspaceId;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    doReturn(true).when(mockSamService).isAuthorized(any(), any(), any(), any());
    AuthenticatedUserRequest fakeAuthentication = new AuthenticatedUserRequest();
    fakeAuthentication
        .token(Optional.of("fake-token"))
        .email("fake@email.com")
        .subjectId("fakeID123");
    when(mockAuthenticatedUserRequestFactory.from(any())).thenReturn(fakeAuthentication);
  }

  // TODO: need a best case test, once an endpoint for creating a DataReference exists.

  @Test
  public void enumerateFailsUnauthorized() throws Exception {
    doReturn(false).when(mockSamService).isAuthorized(any(), any(), any(), any());
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId.toString(), 0, 10, "ALL")))
            .andExpect(status().is(401))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getMessage(), containsString("not authorized"));
  }

  @Test
  public void enumerateFailsWithMissingWorkspace() throws Exception {
    String fakeId = UUID.randomUUID().toString();
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(fakeId, 0, 10, "ALL")))
            .andExpect(status().is(404))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getMessage(), containsString(fakeId));
  }

  @Test
  public void enumerateFailsWithInvalidOffset() throws Exception {
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId.toString(), -1, 10, "ALL")))
            .andExpect(status().is(400))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getCauses().get(0), containsString("offset"));
  }

  @Test
  public void enumerateFailsWithInvalidLimit() throws Exception {
    MvcResult failResult =
        mvc.perform(get(buildEnumerateEndpoint(workspaceId.toString(), 0, 0, "ALL")))
            .andExpect(status().is(400))
            .andReturn();
    ErrorReport validationError =
        objectMapper.readValue(failResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(validationError.getCauses().get(0), containsString("limit"));
  }

  private String buildEnumerateEndpoint(
      String workspaceId, int offset, int limit, String filterControlled) {
    return "/api/v1/workspaces/"
        + workspaceId
        + "/datareferences?offset="
        + offset
        + "&limit="
        + limit
        + "&filterControlled="
        + filterControlled;
  }
}
