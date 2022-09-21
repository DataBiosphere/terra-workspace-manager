package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@Tag("azure-unit")
@AutoConfigureMockMvc
@ActiveProfiles({"azure-unit-test", "unit-test"})
public class BaseAzureUnitTest extends BaseTest {}
