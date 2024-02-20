package bio.terra.workspace.service.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.StatusCheckConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class BaseStatusServiceTest extends BaseUnitTest {

  private static final int STALENESS = 7;
  private static final StatusCheckConfiguration configuration = new StatusCheckConfiguration();

  static {
    configuration.setEnabled(true);
    configuration.setPollingIntervalSeconds(5);
    configuration.setStartupWaitSeconds(1);
    configuration.setStalenessThresholdSeconds(STALENESS);
  }

  @Test
  void testSingleComponent() {
    BaseStatusService statusService = new BaseStatusService(configuration);
    statusService.registerStatusCheck("okcheck", this::okStatusCheck);
    statusService.checkStatus();
    assertTrue(statusService.getCurrentStatus());

    statusService.registerStatusCheck("notokcheck", this::notOkStatusCheck);
    statusService.checkStatus();
    assertFalse(statusService.getCurrentStatus());
  }

  @Test
  void testStaleness() throws InterruptedException {
    BaseStatusService statusService = new BaseStatusService(configuration);
    statusService.registerStatusCheck("okcheck", this::okStatusCheck);
    statusService.checkStatus();
    TimeUnit.SECONDS.sleep(STALENESS + 2);
    assertFalse(statusService.getCurrentStatus());
    statusService.checkStatus();
    assertTrue(statusService.getCurrentStatus());
  }

  private Boolean okStatusCheck() {
    return true;
  }

  private Boolean notOkStatusCheck() {
    return false;
  }
}
