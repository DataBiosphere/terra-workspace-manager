package bio.terra.workspace.common;

import bio.terra.workspace.app.Main;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ActiveProfiles({"test", "human-readable-logging"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    properties = {
      "spring.cloud.gcp.credentials.location=",
      // Disable instrumentation for spring-webmvc because pact still uses javax libs which causes
      // opentelemetry to try to load the same bean name twice, once for javax and once for jakarta
      "otel.instrumentation.spring-webmvc.enabled=false"
    },
    classes = Main.class)
// Configure MockMvc not to print additional debugging information. Otherwise, this will print out
// request headers including test user access tokens, which should not be written to test output.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public class BaseTest {}
