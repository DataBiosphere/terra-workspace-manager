package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** Base class for (GCP) unit tests: not connected (to GCP) */
@Tag("unit")
@ActiveProfiles("unit-test")
public class BaseUnitTest extends BaseUnitTestMocks {}
