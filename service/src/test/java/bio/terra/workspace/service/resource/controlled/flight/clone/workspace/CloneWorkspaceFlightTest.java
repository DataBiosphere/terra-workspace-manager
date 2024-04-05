package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.*;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class CloneWorkspaceFlightTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightBeanBag flightBeanBag;
  @Mock private AuthenticatedUserRequest userRequest;

  @Test
  public void testCreateFlightSteps() {
    var expectedSteps =
        new ArrayList<>(
            List.of(
                CloneAllFoldersStep.class,
                FindResourcesToCloneStep.class,
                CreateIdsForFutureStepsStep.class,
                LaunchCloneAllResourcesFlightStep.class,
                AwaitCloneAllResourcesFlightStep.class));
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(SOURCE_WORKSPACE_ID, UUID.randomUUID());
    inputs.put(WORKSPACE_ID, UUID.randomUUID());
    var flight = new CloneWorkspaceFlight(inputs, flightBeanBag);

    assertEquals(expectedSteps.size(), flight.getSteps().size());
    assertEquals(
        0L,
        flight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }
}
