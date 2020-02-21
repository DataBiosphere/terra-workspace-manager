package bio.terra.workspace.service.create;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
    MvcResult firstResult = mvc.perform(post("/api/v1/create"))
        .andExpect(status().isOk())
        .andReturn();
    MvcResult secondResult = mvc.perform(post("/api/v1/create"))
        .andExpect(status().isOk())
        .andReturn();

    CreatedWorkspace firstWorkspace = objectMapper
        .readValue(firstResult.getResponse().getContentAsString(), CreatedWorkspace.class);
    CreatedWorkspace secondWorkspace = objectMapper
        .readValue(secondResult.getResponse().getContentAsString(), CreatedWorkspace.class);
    assertThat("First UUID is not empty or null", firstWorkspace.getId(), not(blankOrNullString()));
    assertThat("Second UUID is not empty or null", secondWorkspace.getId(),
        not(blankOrNullString()));
    assertThat("UUIDs don't match", firstWorkspace.getId(), not(equalTo(secondWorkspace.getId())));
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
