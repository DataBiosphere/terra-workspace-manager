package bio.terra.workspace.service.create;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.service.create.exception.SamApiException;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
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
  // TODO: these tests currently fail. The API was changed in this revision, but the create endpoint
  // is updated in a followup change.

  @Autowired private MockMvc mvc;

  @MockBean private SamService mockSam;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CreateService createService;

  @BeforeEach
  public void setup() {
    doNothing().when(mockSam).createWorkspaceWithDefaults(any(), any());
  }

  @Test
  public void testReturnedUUID() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());
    MvcResult firstResult =
        mvc.perform(
                post("/api/v1/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();

    CreatedWorkspace workspace =
        objectMapper.readValue(
            firstResult.getResponse().getContentAsString(), CreatedWorkspace.class);

    assertThat("First UUID is not empty or null", workspace.getId(), not(blankOrNullString()));
  }

  @Test
  public void testWithSpendProfileAndPolicies() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.of(UUID.randomUUID()));
    body.setPolicies(JsonNullable.of(Collections.singletonList(UUID.randomUUID())));
    MvcResult result =
        mvc.perform(
                post("/api/v1/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    CreatedWorkspace workspace =
        objectMapper.readValue(result.getResponse().getContentAsString(),
CreatedWorkspace.class);
    assertThat("UUID is not empty or null", workspace.getId(), not(blankOrNullString()));
  }

  @Test
  public void testUnauthorizedUserIsRejected() throws Exception {
    String errorMsg = "fake SAM error message";
    doThrow(new SamApiException(errorMsg)).when(mockSam).createWorkspaceWithDefaults(any(),
any());

    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());
    MvcResult result =
        mvc.perform(
                post("/api/v1/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport samError =
        objectMapper.readValue(result.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(samError.getMessage(), equalTo(errorMsg));
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
