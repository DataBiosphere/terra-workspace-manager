package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetPetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("unit")
public class AssignAndRemoveManagedIdentityAzureVmStepTest extends BaseAzureSpringBootUnitTest {
  private static final String STUB_STRING_RETURN = "stubbed-return";
  private static final String STUB_STRING_PET_MANAGED_IDENTITY = "stubbed-petManagedIdentity";
  private static final String STUB_STRING_OTHER_MANAGED_IDENTITY = "stubbed-otherManagedIdentity";
  private static final String STUB_ASSIGNED_USER_EMAIL = "user@fake.com";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private Identity mockIdentity;
  @Mock private VirtualMachines mockVms;
  @Mock private VirtualMachine mockVm;
  @Mock private VirtualMachine.Update mockVmStageUpdate1; // update

  @Mock
  private VirtualMachine.Update
      mockVmStageUpdate2; // update.withExistingUserAssignedManagedServiceIdentity

  @Mock
  private VirtualMachine.Update
      mockVmStageUpdate3; // update.withoutUserAssignedManagedServiceIdentity

  @Mock private ControlledAzureVmResource mockAzureVmResource;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;
  @Mock private WsmControlledResourceFields mockControlledResourceFields;

  @BeforeEach
  public void setup() {
    // Computer manager mocks
    when(mockCrlService.getComputeManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockComputeManager);
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);

    when(mockComputeManager.virtualMachines()).thenReturn(mockVms);

    // Managed service identity mocks
    when(mockCrlService.getMsiManager(any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getById(anyString())).thenReturn(mockIdentity);

    // Azure vm update mocks
    when(mockVms.getByResourceGroup(anyString(), anyString())).thenReturn(mockVm);
    when(mockVm.update()).thenReturn(mockVmStageUpdate1);
    when(mockVmStageUpdate1.withExistingUserAssignedManagedServiceIdentity(
            any(com.azure.resourcemanager.msi.models.Identity.class)))
        .thenReturn(mockVmStageUpdate2);

    when(mockAzureVmResource.getVmName()).thenReturn(STUB_STRING_RETURN);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    // Flight context mocks
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockWorkingMap.get(GetPetManagedIdentityStep.MANAGED_IDENTITY_RESOURCE_ID, String.class))
        .thenReturn(STUB_STRING_PET_MANAGED_IDENTITY);
  }

  @Test
  void assignUserAssignedManagedIdentityToVm() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure assignment call was made correctly
    verify(mockVmStageUpdate1).withExistingUserAssignedManagedServiceIdentity(mockIdentity);
  }

  @Test
  void assignUserAssignedManagedIdentityToVm_fromRequest() throws InterruptedException {
    when(mockAzureVmResource.getUserAssignedIdentities())
        .thenReturn(List.of(STUB_STRING_PET_MANAGED_IDENTITY, STUB_STRING_OTHER_MANAGED_IDENTITY));

    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure assignment call was made correctly
    verify(mockVmStageUpdate1, times(2))
        .withExistingUserAssignedManagedServiceIdentity(mockIdentity);
  }

  @Test
  void assignUserAssignedManagedIdentityToVm_alreadyAssigned() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    Set<String> userAssignedManagedIdentities =
        Set.of(STUB_STRING_PET_MANAGED_IDENTITY, STUB_STRING_OTHER_MANAGED_IDENTITY);
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure assignment call was made correctly
    verify(mockVm).userAssignedManagedServiceIdentityIds();
    verifyNoInteractions(mockVmStageUpdate2);
    verifyNoInteractions(mockVmStageUpdate1);
  }

  @Test
  void assignUserAssignedManagedIdentityToVm_noAssignedUser() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    when(mockWorkingMap.get(GetPetManagedIdentityStep.MANAGED_IDENTITY_RESOURCE_ID, String.class))
        .thenReturn(null);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify no Azure VM update call was made
    verifyNoInteractions(mockVmStageUpdate1);
  }

  @Test
  void undoAssignUserAssignedManagedIdentityToVm() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    Set<String> userAssignedManagedIdentities = Set.of(STUB_STRING_PET_MANAGED_IDENTITY);
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);
    when(mockVmStageUpdate1.withoutUserAssignedManagedServiceIdentity(anyString()))
        .thenReturn(mockVmStageUpdate3);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.undoStep(mockFlightContext);

    // Verify undo step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure calls were made correctly
    verify(mockVm).userAssignedManagedServiceIdentityIds();
    verify(mockVmStageUpdate1)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_PET_MANAGED_IDENTITY);
  }

  @Test
  void undoAssignUserAssignedManagedIdentityToVm_noIdentitiesAssigned_noVmUpdate()
      throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(mockAzureConfig, mockCrlService, mockAzureVmResource);

    Set<String> userAssignedManagedIdentities = Set.of();
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify no Azure VM update call was made
    verifyNoInteractions(mockVmStageUpdate1);
  }

  @Test
  public void removeManagedIdentitiesAzureFromVm() throws InterruptedException {
    Set<String> userAssignedManagedIdentities = Set.of(STUB_STRING_PET_MANAGED_IDENTITY);
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);
    when(mockVmStageUpdate1.withoutUserAssignedManagedServiceIdentity(anyString()))
        .thenReturn(mockVmStageUpdate3);

    var removeManagedIdentitiesAzureVmStep =
        new RemoveManagedIdentitiesAzureVmStep(
            mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = removeManagedIdentitiesAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure update() without User Assigned MSI was called
    verify(mockVmStageUpdate1)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_PET_MANAGED_IDENTITY);
  }

  @Test
  public void removeManagedIdentitiesAzureFromVm_removeAll() throws InterruptedException {
    Set<String> userAssignedManagedIdentities =
        Set.of(STUB_STRING_PET_MANAGED_IDENTITY, STUB_STRING_OTHER_MANAGED_IDENTITY);
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);
    when(mockVmStageUpdate1.withoutUserAssignedManagedServiceIdentity(anyString()))
        .thenReturn(mockVmStageUpdate3);

    var removeManagedIdentitiesAzureVmStep =
        new RemoveManagedIdentitiesAzureVmStep(
            mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = removeManagedIdentitiesAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure update() without User Assigned MSI was called
    verify(mockVmStageUpdate1)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_PET_MANAGED_IDENTITY);
    verify(mockVmStageUpdate1)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_OTHER_MANAGED_IDENTITY);
  }

  @Test
  public void removeManagedIdentitiesAzureFromVm_emptySet() throws InterruptedException {
    Set<String> userAssignedManagedIdentities = Set.of();
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);

    var removeManagedIdentitiesAzureVmStep =
        new RemoveManagedIdentitiesAzureVmStep(
            mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = removeManagedIdentitiesAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure update() without User Assigned MSI was NOT called
    verifyNoInteractions(mockVmStageUpdate1);
  }
}
