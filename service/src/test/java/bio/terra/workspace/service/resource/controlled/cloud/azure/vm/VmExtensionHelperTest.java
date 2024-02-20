package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineExtension;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;

public class VmExtensionHelperTest extends BaseAzureUnitTest {

  @Mock ComputeManager computeManager;
  @Mock VirtualMachines virtualMachines;

  @BeforeEach
  void setup() {
    computeManager = mock(ComputeManager.class);
    virtualMachines = mock(com.azure.resourcemanager.compute.models.VirtualMachines.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);
  }

  @Test
  void maybeInstallExtension_doesNothingWithNoScriptExtensionInput() {
    var computeManager = mock(ComputeManager.class);
    var vmExtensionHelper = new VmExtensionHelper();
    var creationParams = new ApiAzureVmCreationParameters();

    vmExtensionHelper.maybeInstallExtension(creationParams, "vmId", computeManager);

    verifyNoInteractions(computeManager);
  }

  @ParameterizedTest
  @EnumSource(ExtensionStatus.class)
  void maybeInstallExtension_installsWithExtensionConfig(ExtensionStatus extensionStatus) {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake_extension");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var virtualMachine =
        mockVmWithExtensionState(computeManager, "fake_vmid", "fake_extension", extensionStatus);
    var mockCustomScriptExtension =
        mock(VirtualMachineExtension.UpdateDefinitionStages.WithAttach.class);
    var mockUpdate = mock(VirtualMachine.Update.class);
    when(mockCustomScriptExtension.attach()).thenReturn(mockUpdate);
    when(virtualMachine
            .update()
            .defineNewExtension(nullable(String.class))
            .withPublisher(nullable(String.class))
            .withType(nullable(String.class))
            .withVersion(nullable(String.class))
            .withPublicSettings(any())
            .withProtectedSettings(any())
            .withTags(any()))
        .thenReturn(mockCustomScriptExtension);
    var vmExtensionHelper = new VmExtensionHelper();

    var result =
        vmExtensionHelper.maybeInstallExtension(creationParams, "fake_vmid", computeManager);

    switch (extensionStatus) {
      case NOT_PRESENT:
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
        verify(mockUpdate).apply();
        break;
      case CREATING:
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
        verifyNoInteractions(mockUpdate);
        break;
      case FAILED:
      case CANCELED:
        verify(virtualMachine.update().withoutExtension(anyString())).apply();
        verify(mockUpdate).apply();
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
        break;
      case SUCCEEDED:
        verifyNoInteractions(mockUpdate);
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
        break;
    }
  }

  @Test
  void maybeInstallExtension_handlesConflictManagementException() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var virtualMachine = mockVmWithExtensionState(computeManager, "fake_vmid", "fake", null);

    var mockUpdate = mock(VirtualMachine.Update.class);
    var mockCustomScriptExtension =
        mock(VirtualMachineExtension.UpdateDefinitionStages.WithAttach.class);
    when(mockCustomScriptExtension.attach()).thenReturn(mockUpdate);
    when(virtualMachine
            .update()
            .defineNewExtension(nullable(String.class))
            .withPublisher(nullable(String.class))
            .withType(nullable(String.class))
            .withVersion(nullable(String.class))
            .withPublicSettings(any())
            .withProtectedSettings(any())
            .withTags(any()))
        .thenReturn(mockCustomScriptExtension);
    var mockManagementError = mock(ManagementError.class);
    var mockManagementException = mock(ManagementException.class);
    when(mockManagementError.getCode()).thenReturn("Conflict");
    when(mockManagementException.getValue()).thenReturn(mockManagementError);
    when(mockUpdate.apply()).thenThrow(mockManagementException);

    var vmExtensionHelper = new VmExtensionHelper();

    var result =
        vmExtensionHelper.maybeInstallExtension(creationParams, "fake_vmid", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void maybeInstallExtension_failsOnProvisioningError() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var virtualMachine = mockVmWithExtensionState(computeManager, "fake_vmid", "fake", null);

    var mockUpdate = mock(VirtualMachine.Update.class);
    var mockCustomScriptExtension =
        mock(VirtualMachineExtension.UpdateDefinitionStages.WithAttach.class);
    when(mockCustomScriptExtension.attach()).thenReturn(mockUpdate);
    when(virtualMachine
            .update()
            .defineNewExtension(nullable(String.class))
            .withPublisher(nullable(String.class))
            .withType(nullable(String.class))
            .withVersion(nullable(String.class))
            .withPublicSettings(any())
            .withProtectedSettings(any())
            .withTags(any()))
        .thenReturn(mockCustomScriptExtension);
    var mockManagementError = mock(ManagementError.class);
    var mockManagementException = mock(ManagementException.class);
    when(mockManagementError.getCode()).thenReturn("VMExtensionProvisioningError");
    when(mockManagementError.getMessage()).thenReturn("VMExtensionProvisioningError");
    when(mockManagementException.getValue()).thenReturn(mockManagementError);
    when(mockManagementException.getMessage()).thenReturn("VMExtensionProvisioningError");
    when(mockUpdate.apply()).thenThrow(mockManagementException);

    var vmExtensionHelper = new VmExtensionHelper();

    var result =
        vmExtensionHelper.maybeInstallExtension(creationParams, "fake_vmid", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void maybeInstallExtension_skipsIfExistingInstallation() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    mockVmWithExtensionState(computeManager, "fake_vmid", "fake", ExtensionStatus.SUCCEEDED);
    var vmExtensionHelper = new VmExtensionHelper();

    var result =
        vmExtensionHelper.maybeInstallExtension(creationParams, "fake_vmid", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void maybeInstallExtension_returnsRetryIfInstalling() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    mockVmWithExtensionState(computeManager, "fake_vmid", "fake", ExtensionStatus.CREATING);

    var vmExtensionHelper = new VmExtensionHelper();

    var result =
        vmExtensionHelper.maybeInstallExtension(creationParams, "fake_vmid", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void maybeUninstallExtension_doesNothingWithNoScriptExtensionInput() {
    var computeManager = mock(ComputeManager.class);
    var vmExtensionHelper = new VmExtensionHelper();
    var creationParams = new ApiAzureVmCreationParameters();

    vmExtensionHelper.maybeUninstallExtension(creationParams, "vmId", computeManager);

    verifyNoInteractions(computeManager);
  }

  @ParameterizedTest
  @EnumSource(ExtensionStatus.class)
  void maybeUninstallExtension_uninstallsExtension(ExtensionStatus extensionStatus) {
    var computeManager = mock(ComputeManager.class);
    var vmExtensionHelper = new VmExtensionHelper();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.setName("fake");
    var creationParams = new ApiAzureVmCreationParameters();
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);
    var virtualMachine =
        mockVmWithExtensionState(computeManager, "fake_vm", "fake", extensionStatus);

    var result =
        vmExtensionHelper.maybeUninstallExtension(creationParams, "fake_vm", computeManager);

    switch (extensionStatus) {
      case FAILED:
      case CANCELED:
      case SUCCEEDED:
        {
          Mockito.verify(
                  virtualMachine
                      .update()
                      .withoutExtension(ArgumentMatchers.eq(customScriptExtensionConfig.getName())))
              .apply();
          assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
          break;
        }
      case CREATING:
        Mockito.verifyNoInteractions(
            virtualMachine
                .update()
                .withoutExtension(ArgumentMatchers.eq(customScriptExtensionConfig.getName()))
                .apply());
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
        break;
      case NOT_PRESENT:
        Mockito.verifyNoInteractions(
            virtualMachine
                .update()
                .withoutExtension(ArgumentMatchers.eq(customScriptExtensionConfig.getName()))
                .apply());
        assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    }
  }

  private VirtualMachine mockVmWithExtensionState(
      ComputeManager computeManager, String vmId, String extensionName, ExtensionStatus status) {
    var virtualMachines = mock(com.azure.resourcemanager.compute.models.VirtualMachines.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);
    var virtualMachine =
        mock(com.azure.resourcemanager.compute.models.VirtualMachine.class, RETURNS_DEEP_STUBS);
    when(virtualMachines.getById(vmId)).thenReturn(virtualMachine);

    if (extensionName != null && status != null) {
      var mockExtension = mock(VirtualMachineExtension.class);
      when(virtualMachine.listExtensions())
          .thenReturn(Collections.singletonMap(extensionName, mockExtension));
      when(mockExtension.provisioningState()).thenReturn(status.toString());
    } else {
      when(virtualMachine.listExtensions()).thenReturn(Collections.emptyMap());
    }

    return virtualMachine;
  }
}
