package bio.terra.workspace.common;

import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to Azure */
@ActiveProfiles({"azure-test", "connected-test"})
public class BaseAzureConnectedTest extends BaseTest {}
