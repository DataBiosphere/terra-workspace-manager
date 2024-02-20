package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class CopyAzureStorageContainerDefinitionStepTest extends BaseSpringBootAzureUnitTest {

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
    workspaceId = UUID.randomUUID();
    flightContext = mock(FlightContext.class);

    workingMap = new FlightMap();
    inputParams = new FlightMap();
    inputParams.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);

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
  void doStep_clonesDefinition() throws InterruptedException {
    var destResourceId = UUID.randomUUID();
    var destResourceName = "fake-dest-resource-name";
    var destContainerName = "fake-dest-storage-container-name";
    var sharedAccountRegion = "centralus";

    inputParams.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, destResourceId);
    inputParams.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, destResourceName);
    inputParams.put(ControlledResourceKeys.DESTINATION_CONTAINER_NAME, destContainerName);
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    workingMap.put(
        ControlledResourceKeys.SHARED_STORAGE_ACCOUNT,
        new ApiAzureLandingZoneDeployedResource()
            .resourceId(UUID.randomUUID().toString())
            .region(sharedAccountRegion));
    var sourceContainer =
        buildContainerResource(
            "fake-source-container",
            "fake-source-resource-name",
            UUID.randomUUID(),
            UUID.randomUUID());
    var createdContainer =
        buildContainerResource(destContainerName, destResourceName, destResourceId, workspaceId);

    when(controlledResourceService.createControlledResourceSync(
            any(), any(), eq(userRequest), any()))
        .thenReturn(createdContainer);

    var step =
        new CopyAzureStorageContainerDefinitionStep(
            mockSamService(),
            userRequest,
            sourceContainer,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    var result = step.doStep(flightContext);
    var cloned =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
            ControlledAzureStorageContainerResource.class);

    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_SUCCESS);
    verify(controlledResourceService).createControlledResourceSync(any(), any(), any(), any());
    assertEquals(cloned.getResourceId(), destResourceId);
    assertEquals(cloned.getStorageContainerName(), destContainerName);
    assertEquals(cloned.getName(), destResourceName);
    assertEquals(cloned.getCloningInstructions(), CloningInstructions.COPY_DEFINITION);
    assertEquals(sharedAccountRegion, cloned.getRegion());
  }

  @Test
  void failsIfNoLandingZoneAccount() {
    inputParams.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.randomUUID());
    inputParams.put(ControlledResourceKeys.DESTINATION_CONTAINER_NAME, "fake");
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    var sourceContainer =
        buildContainerResource(
            "fake-source-container",
            "fake-source-resource-name",
            UUID.randomUUID(),
            UUID.randomUUID());

    var step =
        new CopyAzureStorageContainerDefinitionStep(
            mockSamService(),
            userRequest,
            sourceContainer,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    var ex = assertThrows(MissingRequiredFieldsException.class, () -> step.doStep(flightContext));
    assertThat(ex.getMessage(), containsString(ControlledResourceKeys.SHARED_STORAGE_ACCOUNT));
  }

  @Test
  void undoStep_deletesContainer() throws InterruptedException {
    var clonedContainer =
        buildContainerResource(
            "fake-container", "fake-resource", UUID.randomUUID(), UUID.randomUUID());
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedContainer);

    var step =
        new CopyAzureStorageContainerDefinitionStep(
            mockSamService(),
            userRequest,
            null,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    var result = step.undoStep(flightContext);

    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_SUCCESS);
    verify(controlledResourceService).deleteControlledResourceSync(any(), any(), eq(false), any());
  }
}
