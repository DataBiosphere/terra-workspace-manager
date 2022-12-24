package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.BaseTest;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to AWS */
@Tag("aws")
@ActiveProfiles({"aws-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {}
