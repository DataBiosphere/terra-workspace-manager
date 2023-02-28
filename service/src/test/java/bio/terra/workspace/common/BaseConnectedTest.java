package bio.terra.workspace.common;

import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for connected tests to extend.
 *
 * <p>"Connected" tests may call dependencies, like clouds or other services, but are not limited to
 * using the public API.
 */
@ActiveProfiles("connected-test")
public class BaseConnectedTest extends BaseTest {

  public static final String BUFFER_SERVICE_DISABLED_ENVS_REG_EX = "dev|alpha|staging|prod";
}
