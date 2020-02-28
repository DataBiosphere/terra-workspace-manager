package bio.terra.workspace.service.create;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CreateServiceTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private CreateService createService;

  @Test
  public void testReturnedUUID() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.undefined());
    body.setPolicies(JsonNullable.undefined());
    MvcResult firstResult = mvc.perform(post("/api/v1/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andReturn();

    CreatedWorkspace workspace = objectMapper
        .readValue(firstResult.getResponse().getContentAsString(), CreatedWorkspace.class);

    assertThat("UUID is not empty or null", workspace.getId(), not(blankOrNullString()));
  }

  @Test
  public void testWithSpendProfileAndPolicies() throws Exception {
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody();
    body.setId(UUID.randomUUID());
    body.setAuthToken("todo: add token");
    body.setSpendProfile(JsonNullable.of(UUID.randomUUID()));
    body.setPolicies(JsonNullable.of(Collections.singletonList(UUID.randomUUID())));
    MvcResult result = mvc.perform(post("/api/v1/create").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andReturn();
    CreatedWorkspace workspace = objectMapper
        .readValue(result.getResponse().getContentAsString(), CreatedWorkspace.class);
    assertThat("UUID is not empty or null", workspace.getId(), not(blankOrNullString()));
  }

  // TODO: blank tests that should be written as more functionality gets added.
  // @Test
  // public void testUnauthorizedUserIsRejected() {
  // }
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
