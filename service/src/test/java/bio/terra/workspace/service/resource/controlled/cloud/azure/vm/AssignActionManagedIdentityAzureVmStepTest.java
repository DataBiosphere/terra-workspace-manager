package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("unit")
public class AssignActionManagedIdentityAzureVmStepTest extends BaseAzureSpringBootUnitTest {
    private static final String STUB_TENANT = "stub-tenant";
    private static final String STUB_SUBSCRIPTION = "stub-sub";
    private static final String STUB_MRG = "stub-mrg";
    private static final String STUB_SAM_RESOURCE_ID = "stub-storage-access";
    private static final String STUB_ACTION_MANAGED_IDENTITY_ID = "/subscriptions/sub/resourceGroups/mrg/userAssignedManagedIdentity/ident";
    private static final String STUB_VM_NAME = "my-vm";

    @Mock private CrlService mockCrlService;
    @Mock private SamService mockSamService;
    @Mock private AzureConfiguration mockAzureConfig;
    @Mock private ControlledAzureVmResource mockAzureVmResource;
    @Mock private AuthenticatedUserRequest mockUserRequest;
    @Mock private FlightContext mockFlightContext;
    @Mock private FlightMap mockWorkingMap;
    @Mock private AzureCloudContext mockAzureCloudContext;
    @Mock private ComputeManager mockComputeManager;
    @Mock private MsiManager mockMsiManager;
    @Mock private Identities mockIdentities;
    @Mock private Identity mockIdentity;
    @Mock private VirtualMachines mockVms;
    @Mock private VirtualMachine mockVm;
    @Mock private VirtualMachine.Update mockVmStageUpdate1; // update
    @Mock
    private VirtualMachine.Update mockVmStageUpdate2; // update.withExistingUserAssignedManagedServiceIdentity
    @Mock
    private VirtualMachine.Update mockVmStageUpdate3; // update.withoutUserAssignedManagedServiceIdentity


    @BeforeEach
    void setup() throws InterruptedException {
        // Compute manager mocks
        when(mockCrlService.getComputeManager(
                any(AzureCloudContext.class), any(AzureConfiguration.class)))
                .thenReturn(mockComputeManager);
        when(mockComputeManager.virtualMachines()).thenReturn(mockVms);

        // MSI manager mocks
        when(mockCrlService.getMsiManager(any(AzureCloudContext.class), any(AzureConfiguration.class)))
                .thenReturn(mockMsiManager);
        when(mockMsiManager.identities()).thenReturn(mockIdentities);
        when(mockIdentities.getById(anyString())).thenReturn(mockIdentity);

        // Flight context mocks
        when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
        when(mockWorkingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
                .thenReturn(mockAzureCloudContext);
        when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_MRG);
        when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_TENANT);
        when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_SUBSCRIPTION);

        // Sam service mocks
        when(mockSamService.listResourceIds(mockUserRequest, SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT))
                .thenReturn(List.of(STUB_SAM_RESOURCE_ID));
        when(mockSamService.getActionManagedIdentity(mockUserRequest, SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT, STUB_SAM_RESOURCE_ID, SamConstants.SamPrivateAzureStorageAccountAction.IDENTIFY))
                .thenReturn(Optional.of(STUB_ACTION_MANAGED_IDENTITY_ID));

        // Azure vm update mocks
        when(mockVms.getByResourceGroup(anyString(), anyString())).thenReturn(mockVm);
        when(mockVm.update()).thenReturn(mockVmStageUpdate1);
        when(mockVmStageUpdate1.withExistingUserAssignedManagedServiceIdentity(
                any(com.azure.resourcemanager.msi.models.Identity.class)))
                .thenReturn(mockVmStageUpdate2);
        when(mockAzureVmResource.getVmName()).thenReturn(STUB_VM_NAME);
    }

    @Test
    void assignActionManagedIdentityToVm() throws InterruptedException {
        var assignManagedIdentityAzureVmStep =
                new AssignActionManagedIdentitiesVmStep(
                        mockAzureConfig, mockCrlService, mockSamService, mockUserRequest, mockAzureVmResource);

        // Call step
        final StepResult stepResult = assignManagedIdentityAzureVmStep.doStep(mockFlightContext);

        // Verify step returns success
        assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

        // Verify Sam calls were made correctly
        verify(mockSamService)
                .listResourceIds(
                        mockUserRequest, SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT);
        verify(mockSamService)
                .getActionManagedIdentity(
                        mockUserRequest, SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT, STUB_SAM_RESOURCE_ID, SamConstants.SamPrivateAzureStorageAccountAction.IDENTIFY
                );

        // Verify Azure assignment call was made correctly
        verify(mockVmStageUpdate1).withExistingUserAssignedManagedServiceIdentity(mockIdentity);
    }

    @Test
    void assignActionManagedIdentityToVm_alreadyAssigned() {

    }

    @Test
    void assignActionManagedIdentityToVm_multiple() {

    }

    @Test
    void assignActionManagedIdentityToVm_noSamResource() {

    }

    @Test
    void assignActionManagedIdentityToVm_noSamAami() {

    }

    // TODO undo tests

}
