package bio.terra.workspace.service.workspace.flight.azure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.policy.model.*;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.annotations.Unit;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.resource.controlled.exception.RegionNotAllowedException;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.cloud.azure.ValidateLandingZoneAgainstPolicyStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Unit
@ExtendWith(MockitoExtension.class)
public class ValidateLandingZoneAgainstPolicyStepTest {

  @Mock LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock AuthenticatedUserRequest userRequest;
  @Mock TpsApiDispatch tpsApiDispatch;
  UUID workspaceId = UUID.randomUUID();
  @Mock WorkspaceService workspaceService;
  @Mock PolicyValidator policyValidator;
  ValidateLandingZoneAgainstPolicyStep step;
  final String token = "test-token";

  @BeforeEach
  void setup() {
    when(userRequest.getRequiredToken()).thenReturn(token);
    step =
        new ValidateLandingZoneAgainstPolicyStep(
            landingZoneApiDispatch,
            userRequest,
            tpsApiDispatch,
            workspaceId,
            workspaceService,
            policyValidator);
  }

  @Test
  void doStep_happyPathNoDataPolicies() throws Exception {
    var landingZoneId = UUID.randomUUID();
    Workspace workspace = mock();
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    when(landingZoneApiDispatch.getLandingZoneId(new BearerToken(token), workspace))
        .thenReturn(landingZoneId);
    // region validation is done with a static method that makes the external api calls, so we can't
    // mock it directly
    when(landingZoneApiDispatch.getLandingZoneRegionUsingWsmToken(landingZoneId))
        .thenReturn("test-region");
    when(tpsApiDispatch.listValidRegions(workspaceId, CloudPlatform.AZURE))
        .thenReturn(Arrays.asList("test-region"));
    var workspacePao = createPao();
    when(tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE))
        .thenReturn(workspacePao);
    when(tpsApiDispatch.getPao(workspaceId)).thenReturn(workspacePao);
    var result = step.doStep(mock());
    assertEquals(StepResult.getStepResultSuccess(), result);
  }

  @Test
  void doStep_NoMatchingRegion() throws Exception {
    var landingZoneId = UUID.randomUUID();
    Workspace workspace = mock();
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    when(landingZoneApiDispatch.getLandingZoneId(new BearerToken(token), workspace))
        .thenReturn(landingZoneId);
    when(landingZoneApiDispatch.getLandingZoneRegionUsingWsmToken(landingZoneId))
        .thenReturn("test-region");
    when(tpsApiDispatch.listValidRegions(workspaceId, CloudPlatform.AZURE))
        .thenReturn(Arrays.asList("different-test-region"));
    var workspacePao = createPao();
    when(tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE))
        .thenReturn(workspacePao);
    assertThrows(RegionNotAllowedException.class, () -> step.doStep(mock()));
  }

  @Test
  void doStep_validatesProtectedDataLandingZone() throws Exception {
    var landingZoneId = UUID.randomUUID();
    Workspace workspace = mock();

    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    when(landingZoneApiDispatch.getLandingZoneId(new BearerToken(token), workspace))
        .thenReturn(landingZoneId);
    when(landingZoneApiDispatch.getLandingZoneRegionUsingWsmToken(landingZoneId))
        .thenReturn("test-region");
    when(tpsApiDispatch.listValidRegions(workspaceId, CloudPlatform.AZURE))
        .thenReturn(Arrays.asList("test-region"));
    var workspacePao =
        createPao(
            new TpsPolicyInput()
                .namespace(TpsUtilities.TERRA_NAMESPACE)
                .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));
    when(tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE))
        .thenReturn(workspacePao);
    when(tpsApiDispatch.getPao(workspaceId)).thenReturn(workspacePao);
    ApiAzureLandingZone landingZone = mock();
    when(landingZoneApiDispatch.getAzureLandingZone(new BearerToken(token), landingZoneId))
        .thenReturn(landingZone);
    when(policyValidator.validateLandingZoneSupportsProtectedData(landingZone))
        .thenReturn(List.of());

    var result = step.doStep(mock());
    assertEquals(StepResult.getStepResultSuccess(), result);
  }

  @Test
  void doStep_failsOnLZIncompatibleWithPolicy() throws Exception {
    var landingZoneId = UUID.randomUUID();
    Workspace workspace = mock();
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    when(landingZoneApiDispatch.getLandingZoneId(new BearerToken(token), workspace))
        .thenReturn(landingZoneId);
    when(landingZoneApiDispatch.getLandingZoneRegionUsingWsmToken(landingZoneId))
        .thenReturn("test-region");
    when(tpsApiDispatch.listValidRegions(workspaceId, CloudPlatform.AZURE))
        .thenReturn(Arrays.asList("test-region"));
    var workspacePao =
        createPao(
            new TpsPolicyInput()
                .namespace(TpsUtilities.TERRA_NAMESPACE)
                .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));
    when(tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE))
        .thenReturn(workspacePao);
    when(tpsApiDispatch.getPao(workspaceId)).thenReturn(workspacePao);
    ApiAzureLandingZone landingZone = mock();
    when(landingZoneApiDispatch.getAzureLandingZone(new BearerToken(token), landingZoneId))
        .thenReturn(landingZone);
    var validationErrors = List.of("invalid policy");
    when(policyValidator.validateLandingZoneSupportsProtectedData(landingZone))
        .thenReturn(validationErrors);

    var result = step.doStep(mock());
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, result.getStepStatus());
    assertTrue(result.getException().isPresent());
    assertInstanceOf(PolicyConflictException.class, result.getException().get());
  }

  private TpsPaoGetResult createPao(TpsPolicyInput... inputs) {
    TpsPolicyInputs tpsPolicyInputs = new TpsPolicyInputs().inputs(Arrays.stream(inputs).toList());
    return new TpsPaoGetResult()
        .component(TpsComponent.WSM)
        .objectType(TpsObjectType.WORKSPACE)
        .objectId(workspaceId)
        .sourcesObjectIds(Collections.emptyList())
        .attributes(tpsPolicyInputs)
        .effectiveAttributes(tpsPolicyInputs);
  }
}
