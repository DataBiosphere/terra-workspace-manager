package bio.terra.workspace.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.utils.BaseStatusService;
import bio.terra.workspace.common.utils.StatusSubsystem;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class BaseStatusServiceTest extends BaseUnitTest {

  private class BaseStatusServiceTestImpl extends BaseStatusService {
    public BaseStatusServiceTestImpl(List<StatusSubsystem> subsystems) {
      super(/*staleThresholdMillis=*/ 600000);
      for (int i = 0; i < subsystems.size(); i++) {
        registerSubsystem("subsystem" + i, subsystems.get(i));
      }
    }
  }

  @Test
  public void testSingleComponent() throws Exception {
    List<StatusSubsystem> subsystems = new ArrayList<>();
    subsystems.add(new StatusSubsystem(() -> new SystemStatusSystems().ok(true), true));

    BaseStatusServiceTestImpl statusService = new BaseStatusServiceTestImpl(subsystems);
    statusService.checkSubsystems();
    assertTrue(statusService.getCurrentStatus().isOk());
  }

  @Test
  public void testPassesWithNonCriticalFailure() throws Exception {
    List<StatusSubsystem> subsystems = new ArrayList<>();
    subsystems.add(new StatusSubsystem(() -> new SystemStatusSystems().ok(true), true));
    subsystems.add(new StatusSubsystem(() -> new SystemStatusSystems().ok(false), false));

    BaseStatusServiceTestImpl statusService = new BaseStatusServiceTestImpl(subsystems);
    statusService.checkSubsystems();
    assertTrue(statusService.getCurrentStatus().isOk());
  }

  @Test
  public void testNotOkWithCriticalFailure() throws Exception {
    List<StatusSubsystem> subsystems = new ArrayList<>();
    subsystems.add(new StatusSubsystem(() -> new SystemStatusSystems().ok(false), true));
    subsystems.add(new StatusSubsystem(() -> new SystemStatusSystems().ok(true), false));

    BaseStatusServiceTestImpl statusService = new BaseStatusServiceTestImpl(subsystems);
    statusService.checkSubsystems();
    assertFalse(statusService.getCurrentStatus().isOk());
  }

  @Test
  public void testNotOkWithCriticalException() throws Exception {
    List<StatusSubsystem> subsystems = new ArrayList<>();
    Supplier<SystemStatusSystems> exceptionSupplier =
        () -> {
          throw new RuntimeException("oh no");
        };
    subsystems.add(new StatusSubsystem(exceptionSupplier, true));

    BaseStatusServiceTestImpl statusService = new BaseStatusServiceTestImpl(subsystems);
    statusService.checkSubsystems();
    assertFalse(statusService.getCurrentStatus().isOk());
  }

  @Test
  public void testOkWithNonCriticalException() throws Exception {
    List<StatusSubsystem> subsystems = new ArrayList<>();
    Supplier<SystemStatusSystems> exceptionSupplier =
        () -> {
          throw new RuntimeException("oh no");
        };
    subsystems.add(new StatusSubsystem(exceptionSupplier, false));

    BaseStatusServiceTestImpl statusService = new BaseStatusServiceTestImpl(subsystems);
    statusService.checkSubsystems();
    assertTrue(statusService.getCurrentStatus().isOk());
  }
}
