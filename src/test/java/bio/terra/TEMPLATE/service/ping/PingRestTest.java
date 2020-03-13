package bio.terra.TEMPLATE.service.ping;

// TODO: TEMPLATE: This test is an example of a test that sets up the Spring environment
//  with a mock front end. That allows us to make mocked REST API calls and test that
//  the results are correct, in a standalone environment.

import bio.terra.TEMPLATE.app.Main;
import bio.terra.TEMPLATE.generated.model.ErrorReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PingRestTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PingService pingService;

    @Test
    public void testRestPong() throws Exception {
        MvcResult result = mvc.perform(post("/api/template/v1/ping?message=hello"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpServletResponse response = result.getResponse();
        String output = response.getContentAsString();
        assertThat("Result starts with pong", output, startsWith("pong:"));
    }

    @Test
    public void testRestBadPing() throws Exception {
        MvcResult result = mvc.perform(post("/api/template/v1/ping"))
                .andExpect(status().isBadRequest())
                .andReturn();
        MockHttpServletResponse response = result.getResponse();
        ErrorReport errorReport = objectMapper.readValue(response.getContentAsString(), ErrorReport.class);
        assertThat("Correct message text", errorReport.getMessage(), equalTo("No message to ping"));
    }
}
