package bio.terra.workspace.service.workspace.flight.gcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveNativeAccessToPrivateResourcesFlight;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveNativeAccessToPrivateResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RestoreNativeAccessToPrivateResourcesFlight;
import bio.terra.workspace.service.workspace.flight.removeuser.ResourceRolePair;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class RemoveNativeAccessToPrivateResourcesStepTest extends BaseMockitoStrictStubbingTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private ControlledResource mockControlledResource;
  @Mock private FlightBeanBag mockFlightBeanBag;
  @Mock private Stairway mockStairway;

  @Test
  public void testDoSuccessNoSubflight() throws InterruptedException {
    when(mockControlledResource.getResourceId()).thenReturn(UUID.randomUUID());
    when(mockControlledResource.getRemoveNativeAccessSteps(any())).thenReturn(List.of());
    var resourceRolesPairs =
        List.of(new ResourceRolePair(mockControlledResource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(mockControlledResource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockStairway, never()).submit(any(), any(), any());
  }

  @Test
  public void testDoSuccessWithSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getApplicationContext()).thenReturn(mockFlightBeanBag);
    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    var flightState =
        mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.SUCCESS);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    var expectedInputs = new FlightMap();
    expectedInputs.put(ControlledResourceKeys.RESOURCE, resource);
    verify(mockStairway)
        .submit(
            eq(flightState.getFlightId()),
            eq(RemoveNativeAccessToPrivateResourcesFlight.class),
            argThat(inputs -> inputs.getMap().equals(expectedInputs.getMap())));
  }

  @Test
  public void testDoRetryWithSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getApplicationContext()).thenReturn(mockFlightBeanBag);
    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    var flightState =
        mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.SUCCESS);

    doThrow(DuplicateFlightIdException.class)
        .when(mockStairway)
        .submit(eq(flightState.getFlightId()), any(), any());

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  public void testDoFailureWithSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getApplicationContext()).thenReturn(mockFlightBeanBag);
    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.ERROR);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  public void testUndoSuccessWithSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getStairway()).thenReturn(mockStairway);

    mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.SUCCESS);

    var undoFlightState =
        mockFlightState(flightIds.get(resource.getResourceId()) + "-undo", FlightStatus.SUCCESS);

    var result = step.undoStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    var expectedInputs = new FlightMap();
    expectedInputs.put(ControlledResourceKeys.RESOURCE, resource);
    verify(mockStairway)
        .submit(
            eq(undoFlightState.getFlightId()),
            eq(RestoreNativeAccessToPrivateResourcesFlight.class),
            argThat(inputs -> inputs.getMap().equals(expectedInputs.getMap())));
  }

  @Test
  public void testUndoFailureWithSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getStairway()).thenReturn(mockStairway);

    mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.SUCCESS);

    mockFlightState(flightIds.get(resource.getResourceId()) + "-undo", FlightStatus.ERROR);

    var result = step.undoStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  public void testUndoSuccessWithFailedSubflight() throws InterruptedException {
    var resource = createControlledResourceWithNativeAccess();

    var resourceRolesPairs =
        List.of(new ResourceRolePair(resource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(resource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    when(mockFlightContext.getStairway()).thenReturn(mockStairway);

    mockFlightState(flightIds.get(resource.getResourceId()), FlightStatus.ERROR);

    var result = step.undoStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    var expectedInputs = new FlightMap();
    expectedInputs.put(ControlledResourceKeys.RESOURCE, resource);
    verify(mockStairway, never()).submit(any(), any(), any());
  }

  private ControlledResource createControlledResourceWithNativeAccess() {
    return ControlledAzureResourceFixtures
        .makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
            ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
                "owner", List.of("db")),
            UUID.randomUUID())
        .build();
  }

  @NotNull
  private RemoveNativeAccessToPrivateResourcesStep setupStepTest(
      List<ResourceRolePair> resourceRolesPairs, Map<UUID, String> flightIds) {
    createMockFlightContext(resourceRolesPairs, flightIds);
    return new RemoveNativeAccessToPrivateResourcesStep();
  }

  @NotNull
  private FlightState mockFlightState(String flightIds, FlightStatus error)
      throws InterruptedException {
    var flightState = new FlightState();
    flightState.setFlightId(flightIds);
    flightState.setFlightStatus(error);
    when(mockStairway.getFlightState(flightState.getFlightId())).thenReturn(flightState);
    return flightState;
  }

  private void createMockFlightContext(
      List<ResourceRolePair> resourceRolesPairs, Map<UUID, String> flightIds) {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(
            eq(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE), any(TypeReference.class)))
        .thenReturn(resourceRolesPairs);
    when(mockWorkingMap.get(eq(WorkspaceFlightMapKeys.FLIGHT_IDS), any(TypeReference.class)))
        .thenReturn(flightIds);
  }
}
