package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

@Tag("unit")
@ActiveProfiles("unit-test")
public class BaseUnitTest extends BaseTest {}
