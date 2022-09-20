package bio.terra.workspace.common;

import bio.terra.workspace.app.Main;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ActiveProfiles({"test", "human-readable-logging"})
@ContextConfiguration(classes = Main.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"spring.cloud.gcp.credentials.location="})
@AutoConfigureMockMvc
public class BaseTest {}
