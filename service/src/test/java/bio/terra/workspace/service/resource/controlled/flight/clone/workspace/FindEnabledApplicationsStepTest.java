package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class FindEnabledApplicationsStepTest extends BaseUnitTest {

  private static final String FLIGHT_ID = "asdfjkl-qwerty";
  private static final String LEO_ID = "leo";
  private static final String CARMEN_ID = "carmen";
  @Mock private ApplicationDao mockApplicationDao;
  @Mock private FlightContext mockFlightContext;
  @Mock private Stairway mockStairway;
  private FindEnabledApplicationsStep findEnabledApplicationsStep;
  private FlightMap workingMap;
  private WsmApplication enabledApplication;
  private WsmApplication disabledApplication;
  private WsmWorkspaceApplication enabledWorkspaceApplication;
  private WsmWorkspaceApplication disabledWorkspaceApplication;

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

    findEnabledApplicationsStep = new FindEnabledApplicationsStep(mockApplicationDao);
    when((mockFlightContext).getStairway()).thenReturn(mockStairway);
    when((mockStairway).createFlightId()).thenReturn(FLIGHT_ID);

    final FlightMap inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.randomUUID());
    when((mockFlightContext).getInputParameters()).thenReturn(inputParameters);

    workingMap = new FlightMap();
    when((mockFlightContext).getWorkingMap()).thenReturn(workingMap);
  }

  @Test
  public void testDoStep() throws InterruptedException, RetryException {
    final List<WsmWorkspaceApplication> batch1 =
        new ArrayList<>(Collections.nCopies(3, enabledWorkspaceApplication));
    batch1.addAll(Collections.nCopies(3, disabledWorkspaceApplication));

    when((mockApplicationDao).listWorkspaceApplications(any(UUID.class), eq(0), eq(100)))
        .thenReturn(batch1);

    final StepResult stepResult = findEnabledApplicationsStep.doStep(mockFlightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    final List<String> result =
        workingMap.get(WorkspaceFlightMapKeys.APPLICATION_IDS, new TypeReference<>() {});
    final AbleEnum able =
        workingMap.get(
            WorkspaceFlightMapKeys.WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.class);
    assertThat(result, hasSize(3));
    assertEquals(enabledWorkspaceApplication.getApplication().getApplicationId(), result.get(0));
    assertEquals(AbleEnum.ENABLE, able);
  }
}
