package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class DeleteAzureStorageContainerStepTest extends BaseAzureUnitTest {

  private UUID resourceId;
  private String resourceName;
  private UUID workspaceId;
  private FlightContext flightContext;
  private FlightMap workingMap;
  private FlightMap inputParams;
  @Mock private ControlledResourceService controlledResourceService;

  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest()
          .subjectId("fake-sub")
          .email("fake@ecample.com")
          .token(Optional.of("fake-token"));

  @BeforeEach
  void setup() {
    resourceId = UUID.randomUUID();
    workspaceId = UUID.randomUUID();
    resourceName = "sc-%s".formatted(workspaceId.toString());
    flightContext = mock(FlightContext.class);

    workingMap = new FlightMap();
    inputParams = new FlightMap();
    inputParams.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);

    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(flightContext.getInputParameters()).thenReturn(inputParams);
    when(flightContext.getFlightId()).thenReturn("fake-flight-id");
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn("foo@gmail.com");
  }

  private static ControlledAzureStorageContainerResource buildContainerResource(
      String storageContainerName, String resourceName, UUID resourceId, UUID workspaceId) {
    return new ControlledAzureStorageContainerResource.Builder()
        .storageContainerName(storageContainerName)
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .resourceId(resourceId)
                .workspaceUuid(workspaceId)
                .cloningInstructions(CloningInstructions.COPY_DEFINITION)
                .properties(Map.of())
                .name(resourceName)
                .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                .managedBy(ManagedByType.MANAGED_BY_USER)
                .build())
        .build();
  }

  @Test
  void doStep_deletesContainer() throws InterruptedException {
    var createdContainer =
        buildContainerResource("fake-container", resourceName, resourceId, workspaceId);
    workingMap.put(ControlledResourceKeys.AZURE_STORAGE_CONTAINER, createdContainer);

    var step =
        new DeleteAzureStorageContainerStep(resourceName, resourceId, controlledResourceService);

    var result = step.doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    verify(controlledResourceService)
        .deleteControlledResourceSync(eq(workspaceId), eq(resourceId), eq(false), any());
  }

  @Test
  void undoStep_doesNothing() throws InterruptedException {
    var step =
        new DeleteAzureStorageContainerStep("sc-name", resourceId, controlledResourceService);

    var result = step.undoStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
  }
}
