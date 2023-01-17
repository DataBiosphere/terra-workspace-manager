package bio.terra.workspace.common;

import bio.terra.workspace.service.spendprofile.SpendProfileService;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to Azure */
@Tag("azure")
@ActiveProfiles({"azure-test", "connected-test"})
public class BaseAzureConnectedTest extends BaseTest {

  @MockBean private SpendProfileService mockSpendProfileService;

  public SpendProfileService mockSpendProfileService() {
    return mockSpendProfileService;
  }
}
