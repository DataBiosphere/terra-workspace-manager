package bio.terra.workspace.service.workspace.flight.gcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleNone;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.CreateAzureDatabaseStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveNativeAccessToPrivateResourcesStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ResourceRolePair;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
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
  @Mock private Step mockStep;

  @Test
  public void testDoSuccessNoSubflight() throws InterruptedException {
    when(mockControlledResource.getResourceId()).thenReturn(UUID.randomUUID());
    when(mockControlledResource.getRemoveNativeAccessSteps(any())).thenReturn(List.of());
    var resourceRolesPairs = List.of(new ResourceRolePair(mockControlledResource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(mockControlledResource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  public void testDoSuccessWithSubflight() throws InterruptedException {
    when(mockControlledResource.getResourceId()).thenReturn(UUID.randomUUID());
    when(mockControlledResource.getRemoveNativeAccessSteps(any())).thenReturn(List.of(new StepRetryRulePair(mockStep, RetryRuleNone.getRetryRuleNone())));
    var resourceRolesPairs = List.of(new ResourceRolePair(mockControlledResource, ControlledResourceIamRole.WRITER));
    var flightIds = Map.of(mockControlledResource.getResourceId(), UUID.randomUUID().toString());
    var step = setupStepTest(resourceRolesPairs, flightIds);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @NotNull
  private RemoveNativeAccessToPrivateResourcesStep setupStepTest(List<ResourceRolePair> resourceRolesPairs, Map<UUID, String> flightIds) {
    createMockFlightContext(resourceRolesPairs, flightIds);
    return new RemoveNativeAccessToPrivateResourcesStep();
  }

  private void createMockFlightContext(List<ResourceRolePair> resourceRolesPairs, Map<UUID, String> flightIds) {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(eq(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE), any(TypeReference.class)))
        .thenReturn(resourceRolesPairs);
    when(mockWorkingMap.get(eq(WorkspaceFlightMapKeys.FLIGHT_IDS), any(TypeReference.class)))
        .thenReturn(flightIds);
  }
}
