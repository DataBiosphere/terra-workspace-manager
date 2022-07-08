package bio.terra.workspace.common.logging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.Direction;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.UnknownFlightClassNameException;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceActivityLogHookTest extends BaseUnitTest {

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @Autowired private WorkspaceActivityLogHook hook;
  @MockBean private SamService mockSamService;

  @Test
  void createFlightSucceeds_activityLogUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.CREATE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.SUCCESS));
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void deleteFlightSucceeds_activityLogUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.DELETE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.SUCCESS));

    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void unknownFlightSucceeds_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.DELETE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(new FakeFlightContext("TestFlight", inputParams, FlightStatus.SUCCESS)));

    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isEmpty());
  }

  @Test
  void createFlightFails_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.CREATE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.ERROR));

    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteFlightFails_workspaceNotExist_logChangedDate() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.DELETE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getWorkspaceIfExists(workspaceUuid).isEmpty());
    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isPresent());
  }

  @Test
  void unknownFlightFails_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.DELETE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(
                new FakeFlightContext("TestDeleteFlight", inputParams, FlightStatus.ERROR)));

    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  private class FakeFlightContext implements FlightContext {

    private final String flightClassName;
    private final FlightMap inputParams;
    private final FlightStatus status;

    FakeFlightContext(String flightClassName, FlightMap inputMap, FlightStatus flightStatus) {
      this.flightClassName = flightClassName;
      inputParams = inputMap;
      status = flightStatus;
    }

    @Override
    public Object getApplicationContext() {
      return null;
    }

    @Override
    public String getFlightId() {
      return "flight-id";
    }

    @Override
    public String getFlightClassName() {
      return flightClassName;
    }

    @Override
    public FlightMap getInputParameters() {
      return inputParams;
    }

    @Override
    public FlightMap getWorkingMap() {
      return new FlightMap();
    }

    @Override
    public int getStepIndex() {
      return 0;
    }

    @Override
    public FlightStatus getFlightStatus() {
      return status;
    }

    @Override
    public boolean isRerun() {
      return false;
    }

    @Override
    public Direction getDirection() {
      return Direction.DO;
    }

    @Override
    public StepResult getResult() {
      return StepResult.getStepResultSuccess();
    }

    @Override
    public Stairway getStairway() {
      return null;
    }

    @Override
    public List<String> getStepClassNames() {
      return null;
    }

    @Override
    public String getStepClassName() {
      return null;
    }

    @Override
    public String prettyStepState() {
      return null;
    }

    @Override
    public String flightDesc() {
      return null;
    }
  }
}
