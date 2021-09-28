package bio.terra.workspace.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to Azure */
@Tag("azure")
@ActiveProfiles({"azure", "azure-test"})
public class BaseAzureTest extends BaseTest {}
