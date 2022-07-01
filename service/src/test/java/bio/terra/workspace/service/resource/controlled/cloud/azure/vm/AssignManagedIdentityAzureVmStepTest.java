package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class AssignManagedIdentityAzureVmStepTest extends BaseAzureTest {
  private static final String STUB_STRING_RETURN = "stubbed-return";
  private static final String STUB_STRING_MANAGED_IDENTITY = "stubbed-petManagedIdentity";
  private UserAccessUtils userAccessUtils;

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private SamService mockSamService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private Identity mockIdentity;
  @Mock private VirtualMachines mockVms;
  @Mock private VirtualMachine mockVm;
  @Mock private VirtualMachine.Update mockVmStageUpdate1;
  @Mock private VirtualMachine.Update mockVmStageUpdate2;
  // @Mock private VirtualMachine.UpdateStages.WithUserAssignedManagedServiceIdentity
  // mockVmStageWithUami;
  // @Mock private VirtualMachine.UpdateStages.WithSystemAssignedManagedServiceIdentity
  // mockVmStageWithSami;
  @Mock private ControlledAzureVmResource mockAzureVmResource;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() throws InterruptedException {
    // Computer manager mocks
    when(mockCrlService.getComputeManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockComputeManager);
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);

    when(mockComputeManager.virtualMachines()).thenReturn(mockVms);

    // Set SAM service mocks
    when(mockSamService.getOrCreateUserManagedIdentity(
            userAccessUtils.defaultUserAuthRequest(), anyString(), anyString(), anyString()))
        .thenReturn(STUB_STRING_MANAGED_IDENTITY);

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
  }

  @Test
  void assignUserAssignedManagedIdentitytoVm() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            mockSamService,
            userAccessUtils.defaultUserAuthRequest(),
            mockAzureVmResource);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure assignment call was made correctly
    verify(mockVmStageUpdate1).withExistingUserAssignedManagedServiceIdentity(mockIdentity);
  }

  void UndoAssignUserAssignedManagedIdentitytoVm() throws InterruptedException {
    var assignManagedIdentityAzureVmStep =
        new AssignManagedIdentityAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            mockSamService,
            userAccessUtils.defaultUserAuthRequest(),
            mockAzureVmResource);

    final StepResult stepResult = assignManagedIdentityAzureVmStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure assignment call was made correctly
    verify(mockVmStageUpdate2)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_MANAGED_IDENTITY);
    ;
  }

  @Test
  public void RemoveManagedIdentitiesAzureFromVm() throws InterruptedException {
    Set<String> userAssignedManagedIdentities = Set.of(STUB_STRING_MANAGED_IDENTITY);
    when(mockVm.userAssignedManagedServiceIdentityIds()).thenReturn(userAssignedManagedIdentities);

    var removeManagedIdentitiesAzureVmStep =
        new RemoveManagedIdentitiesAzureVmStep(
            mockAzureConfig, mockCrlService, mockAzureVmResource);

    final StepResult stepResult = removeManagedIdentitiesAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure update() without User Assigned MSI was called
    verify(mockVmStageUpdate1)
        .withoutUserAssignedManagedServiceIdentity(STUB_STRING_MANAGED_IDENTITY);
  }
}
