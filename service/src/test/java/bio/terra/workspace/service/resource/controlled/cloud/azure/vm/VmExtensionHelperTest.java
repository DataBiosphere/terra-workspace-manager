package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineExtension;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class VmExtensionHelperTest extends BaseAzureUnitTest {

  @Test
  void maybeInstallExtension_noopWithNoScriptExtension() {
    var computeManager = mock(ComputeManager.class);
    var vmExtensionHelper = new VmExtensionHelper();
    var creationParams = new ApiAzureVmCreationParameters();

    vmExtensionHelper.maybeInstallExtension(creationParams, "vmId", computeManager);

    verifyNoInteractions(computeManager);
  }

  @Test
  void maybeInstallExtension_withExtensionConfig() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var computeManager = mock(ComputeManager.class);
    var virtualMachines = mock(com.azure.resourcemanager.compute.models.VirtualMachines.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);
    var virtualMachine =
        mock(com.azure.resourcemanager.compute.models.VirtualMachine.class, RETURNS_DEEP_STUBS);
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
    when(virtualMachines.getById("vmId")).thenReturn(virtualMachine);
    when(virtualMachine.listExtensions()).thenReturn(Collections.emptyMap());

    var vmExtensionHelper = new VmExtensionHelper();

    var result = vmExtensionHelper.maybeInstallExtension(creationParams, "vmId", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void maybeInstallExtension_handlesExistingInstallation() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var computeManager = mock(ComputeManager.class);
    var virtualMachines = mock(com.azure.resourcemanager.compute.models.VirtualMachines.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);
    var virtualMachine = mock(com.azure.resourcemanager.compute.models.VirtualMachine.class);

    when(virtualMachines.getById("vmId")).thenReturn(virtualMachine);
    var mockExtension = mock(VirtualMachineExtension.class);
    when(virtualMachine.listExtensions())
        .thenReturn(Collections.singletonMap("fake", mockExtension));
    when(mockExtension.provisioningState()).thenReturn("Succeeded");
    var vmExtensionHelper = new VmExtensionHelper();

    var result = vmExtensionHelper.maybeInstallExtension(creationParams, "vmId", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void maybeInstallExtension_returnsRetryIfInstalling() {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtensionConfig = new ApiAzureVmCustomScriptExtension();
    customScriptExtensionConfig.minorVersionAutoUpgrade(true);
    customScriptExtensionConfig.setName("fake");
    creationParams.setCustomScriptExtension(customScriptExtensionConfig);

    var computeManager = mock(ComputeManager.class);
    var virtualMachines = mock(com.azure.resourcemanager.compute.models.VirtualMachines.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);
    var virtualMachine = mock(com.azure.resourcemanager.compute.models.VirtualMachine.class);

    when(virtualMachines.getById("vmId")).thenReturn(virtualMachine);
    var mockExtension = mock(VirtualMachineExtension.class);
    when(virtualMachine.listExtensions())
        .thenReturn(Collections.singletonMap("fake", mockExtension));
    when(mockExtension.provisioningState()).thenReturn("Creating");
    var vmExtensionHelper = new VmExtensionHelper();

    var result = vmExtensionHelper.maybeInstallExtension(creationParams, "vmId", computeManager);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }
}
