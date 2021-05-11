package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("integration-test")
public class BaseIntegrationTest extends BaseTest {}
