package bio.terra.workspace.common;

import bio.terra.workspace.app.Main;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ActiveProfiles({"test", "human-readable-logging"})
@ContextConfiguration(classes = Main.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"spring.cloud.gcp.credentials.location="})
// Configure MockMvc not to print additional debugging information. Otherwise, this will print out
// request headers including test user access tokens, which should not be written to test output.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public class BaseTest {}
