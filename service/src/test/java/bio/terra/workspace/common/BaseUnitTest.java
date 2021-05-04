package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@Tag("unit")
@AutoConfigureMockMvc
@ActiveProfiles("unit-test")
public class BaseUnitTest extends BaseTest {}
