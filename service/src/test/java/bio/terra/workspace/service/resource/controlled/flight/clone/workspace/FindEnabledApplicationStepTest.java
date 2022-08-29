package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.application.able.AbleEnum;
import bio.terra.workspace.service.workspace.model.*;
import java.util.*;
import org.apache.commons.collections.ListUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class FindEnabledApplicationStepTest extends BaseUnitTest {

  private static final String FLIGHT_ID = "asdfjkl-qwerty";
  private static final String LEO_ID = "leo";
  private static final String CARMEN_ID = "carmen";
  @Mock private ApplicationDao mockApplicationDao;
  @Mock private FlightContext mockFlightContext;
  @Mock private Stairway mockStairway;
  private FindEnabledApplicationStep findEnabledApplicationStep;
  private FlightMap workingMap;
  private WsmApplication enabledApplication, disabledApplication;
  private WsmWorkspaceApplication enabledWorkspaceApplication, disabledWorkspaceApplication;

  @BeforeEach
  void setup() throws Exception {
    enabledApplication =
        new WsmApplication()
            .applicationId(LEO_ID)
            .displayName("Leo")
            .description("application execution framework")
            .serviceAccount("leo@terra-dev.iam.gserviceaccount.com")
            .state(WsmApplicationState.OPERATING);
    enabledWorkspaceApplication =
        new WsmWorkspaceApplication().application(enabledApplication).enabled(true);

    disabledApplication =
        new WsmApplication()
            .applicationId(CARMEN_ID)
            .displayName("Carmen")
            .description("musical performance framework")
            .serviceAccount("carmen@terra-dev.iam.gserviceaccount.com")
            .state(WsmApplicationState.DEPRECATED);
    disabledWorkspaceApplication =
        new WsmWorkspaceApplication().application(disabledApplication).enabled(false);

    findEnabledApplicationStep = new FindEnabledApplicationStep(mockApplicationDao);
    doReturn(mockStairway).when(mockFlightContext).getStairway();
    doReturn(FLIGHT_ID).when(mockStairway).createFlightId();

    final FlightMap inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.randomUUID());
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    workingMap = new FlightMap();
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
  }

  @Test
  public void testDoStep() throws InterruptedException, RetryException {
    final List<WsmWorkspaceApplication> batch1 =
        ListUtils.union(
            Collections.nCopies(3, enabledWorkspaceApplication),
            Collections.nCopies(3, disabledWorkspaceApplication));
    doReturn(batch1)
        .when(mockApplicationDao)
        .listWorkspaceApplications(any(UUID.class), eq(0), eq(100));
    final StepResult stepResult = findEnabledApplicationStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    final List<String> result = workingMap.get(WorkspaceFlightMapKeys.APPLICATION_ID, List.class);
    final AbleEnum able =
        workingMap.get(
            WorkspaceFlightMapKeys.WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.class);
    assertThat(result, hasSize(3));
    assertEquals(enabledWorkspaceApplication.getApplication().getApplicationId(), result.get(0));
    assertEquals(AbleEnum.ENABLE, able);
  }
}
