package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS tests. Treat these as connected tests: connected to AWS */
@Tag("aws")
@ActiveProfiles({"aws-test"})
public class BaseAwsConnectedTest extends BaseTest {}
