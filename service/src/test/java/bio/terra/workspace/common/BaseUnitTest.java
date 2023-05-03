package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for unit tests: not connected to cloud providers. Includes GCP unit tests & others
 * cloud-agnostic WSM component unit tests
 */
@Tag("unit")
@ActiveProfiles("unit-test")
public class BaseUnitTest extends BaseUnitTestMocks {}
