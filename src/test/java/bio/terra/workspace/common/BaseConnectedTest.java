package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for connected tests to extend.
 *
 * <p>"Connected" tests may call dependencies, like clouds or other services, but are not limited to
 * using the public API.
 */
@Tag("connected")
@ActiveProfiles("connected-test")
@AutoConfigureMockMvc
public class BaseConnectedTest extends BaseTest {}
