package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class FindResourcesToCloneStepTest extends BaseSpringBootUnitTest {

  private static final String FLIGHT_ID = "asdfjkl-qwerty";
  @Mock private ResourceDao mockResourceDao;
  @Mock private FlightContext mockFlightContext;
  @Mock private Stairway mockStairway;
  private FindResourcesToCloneStep findResourcesToCloneStep;
  private FlightMap workingMap;
  private WsmResource resource;

  @BeforeEach
  public void setup() {
    resource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(UUID.randomUUID())
            .build();

    findResourcesToCloneStep = new FindResourcesToCloneStep(mockResourceDao);
    doReturn(mockStairway).when(mockFlightContext).getStairway();
    doReturn(FLIGHT_ID).when(mockStairway).createFlightId();

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.randomUUID());
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    workingMap = new FlightMap();
    workingMap.put(
        WorkspaceFlightMapKeys.FolderKeys.FOLDER_IDS_TO_CLONE_MAP, new HashMap<String, String>());
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
  }

  @Test
  public void testDoStep_largeBatch() throws InterruptedException, RetryException {
    List<WsmResource> batch1 = Collections.nCopies(100, resource);
    List<WsmResource> batch2 = Collections.nCopies(20, resource);
    doReturn(batch1)
        .when(mockResourceDao)
        .enumerateResources(any(UUID.class), eq(null), eq(null), eq(0), eq(100));
    doReturn(batch2)
        .when(mockResourceDao)
        .enumerateResources(any(UUID.class), eq(null), eq(null), eq(100), eq(100));

    StepResult stepResult = findResourcesToCloneStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    List<ResourceCloneInputs> result =
        workingMap.get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});
    assertThat(result, hasSize(120));
    assertTrue(resource.partialEqual(result.get(0).getResource()));
    assertEquals(FLIGHT_ID, result.get(0).getFlightId());
  }

  @Test
  public void testDoStep_smallBatch() throws InterruptedException, RetryException {
    List<WsmResource> batch1 = Collections.nCopies(3, resource);
    doReturn(batch1)
        .when(mockResourceDao)
        .enumerateResources(any(UUID.class), eq(null), eq(null), eq(0), eq(100));
    StepResult stepResult = findResourcesToCloneStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    List<ResourceCloneInputs> result =
        workingMap.get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});
    assertThat(result, hasSize(3));
    assertTrue(resource.partialEqual(result.get(0).getResource()));
    assertEquals(FLIGHT_ID, result.get(0).getFlightId());
  }
}
