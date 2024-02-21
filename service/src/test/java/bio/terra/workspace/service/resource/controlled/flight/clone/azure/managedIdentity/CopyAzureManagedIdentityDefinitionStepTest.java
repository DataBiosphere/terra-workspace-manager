package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azureUnit")
public class CopyAzureManagedIdentityDefinitionStepTest extends BaseAzureSpringBootUnitTest {

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
    inputParams.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);

    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(flightContext.getInputParameters()).thenReturn(inputParams);
    when(flightContext.getFlightId()).thenReturn("fake-flight-id");
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn("foo@gmail.com");
  }

  private static ControlledAzureManagedIdentityResource buildIdentityResource(
      String managedIdentityName, String resourceName, UUID resourceId, UUID workspaceId) {
    return ControlledAzureManagedIdentityResource.builder()
        .managedIdentityName(managedIdentityName)
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
    var identityName = "idappname";
    var sourceManagedIdentityName = "id%s".formatted(UUID.randomUUID().toString());
    var destManagedIdentityName = "id%s".formatted(UUID.randomUUID().toString());
    var destResourceId = UUID.randomUUID();

    inputParams.put(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destResourceId);
    inputParams.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, identityName);
    inputParams.put(ControlledResourceKeys.DESTINATION_IDENTITY_NAME, destManagedIdentityName);
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    var sourceIdentity =
        buildIdentityResource(
            sourceManagedIdentityName, identityName, UUID.randomUUID(), UUID.randomUUID());
    var createdContainer =
        buildIdentityResource(destManagedIdentityName, identityName, destResourceId, workspaceId);

    when(controlledResourceService.createControlledResourceSync(
            any(), any(), eq(userRequest), any()))
        .thenReturn(createdContainer);

    var step =
        new CopyAzureManagedIdentityDefinitionStep(
            mockSamService(),
            userRequest,
            sourceIdentity,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    var result = step.doStep(flightContext);
    var cloned =
        workingMap.get(
            ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
            ControlledAzureManagedIdentityResource.class);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    verify(controlledResourceService).createControlledResourceSync(any(), any(), any(), any());
    assertNotNull(cloned);
    assertEquals(destResourceId, cloned.getResourceId());
    assertEquals(destManagedIdentityName, cloned.getManagedIdentityName());
    assertEquals(identityName, cloned.getName());
    assertEquals(CloningInstructions.COPY_DEFINITION, cloned.getCloningInstructions());
  }

  @Test
  void undoStep_deletesIdentity() throws InterruptedException {
    var clonedIdentity =
        buildIdentityResource("idfake-mi", "fake-resource", UUID.randomUUID(), UUID.randomUUID());
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedIdentity);

    var step =
        new CopyAzureManagedIdentityDefinitionStep(
            mockSamService(),
            userRequest,
            null,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    var result = step.undoStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    verify(controlledResourceService).deleteControlledResourceSync(any(), any(), eq(false), any());
  }
}
