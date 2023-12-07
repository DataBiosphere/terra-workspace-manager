package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class InstallCustomVmExtensionTest extends BaseAzureUnitTest {

  @Mock private AzureConfiguration azureConfig;
  @Mock private CrlService crlService;
  @Mock private FlightContext flightContext;

  @BeforeEach
  void setup() {
    flightContext = spy(FlightContext.class);
    var workingMap = new FlightMap();
    var azureCloudContext = new AzureCloudContext(null, null);
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, azureCloudContext);
    workingMap.put(AzureVmHelper.WORKING_MAP_VM_ID, "vmId");
    var inputParameters = new FlightMap();
    when(flightContext.getInputParameters()).thenReturn(inputParameters);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
  }

  @Test
  void installExtension_withExtensionConfig() throws InterruptedException {
    var creationParams = new ApiAzureVmCreationParameters();
    var customScriptExtension = new ApiAzureVmCustomScriptExtension();
    creationParams.setCustomScriptExtension(customScriptExtension);
    flightContext
        .getInputParameters()
        .put(WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParams);

    var vmExtensionHelper = mock(VmExtensionHelper.class);
    when(vmExtensionHelper.maybeInstallExtension(eq(creationParams), any(), any(), any()))
        .thenReturn(StepResult.getStepResultSuccess());
    var step = new InstallCustomVmExtension(azureConfig, crlService, vmExtensionHelper);

    step.doStep(flightContext);

    verify(vmExtensionHelper).maybeInstallExtension(eq(creationParams), any(), any(), any());
  }

  @Test
  void installExtension_undoStep() throws InterruptedException {
    var vmExtensionHelper = mock(VmExtensionHelper.class);
    var step = new InstallCustomVmExtension(azureConfig, crlService, vmExtensionHelper);

    step.undoStep(flightContext);

    verify(vmExtensionHelper).maybeUninstallExtension(any(), any(), any());
  }
}
