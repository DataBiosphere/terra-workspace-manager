package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

@Tag("foo")
@ActiveProfiles("connected-test")
public class BaseFooTest extends BaseTest {
    public static final String BUFFER_SERVICE_DISABLED_ENVS_REG_EX = "dev|alpha|staging|prod";

}
