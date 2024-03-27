package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetAzureDiskAttachedVmStepTest {
  private static final String DISK_NAME = "dataDisk";
  private static final String AZURE_SUBSCRIPTION_ID = "subscriptionId";
  private static final String AZURE_RESOURCE_GROUP_ID = "resourceGroupId";

  @Mock private AzureConfiguration azureConfigurationMock;
  @Mock private CrlService crlServiceMock;
  @Mock private ControlledAzureDiskResource controlledAzureDiskResourceMock;
  @Mock private FlightContext flightContextMock;
  @Mock private AzureCloudContext azureCloudContextMock;
  @Mock private FlightMap flightWorkingMapMock;
  @Mock private ComputeManager computeManagerMock;
  @Mock private Disks disksMock;
  @Mock private Disk diskMock;

  private GetAzureDiskAttachedVmStep getAzureDiskAttachedVmStep;

  @BeforeEach
  void setup() {
    getAzureDiskAttachedVmStep =
        new GetAzureDiskAttachedVmStep(
            azureConfigurationMock, crlServiceMock, controlledAzureDiskResourceMock);
  }

  @Test
  void doStep_VmIdentifierSaved() throws InterruptedException {
    final String vmId = "vmId";
    setupCommonMocks();
    when(disksMock.getById(anyString())).thenReturn(diskMock);
    when(diskMock.isAttachedToVirtualMachine()).thenReturn(true);
    when(diskMock.virtualMachineId()).thenReturn(vmId);
    StepResult stepResult = getAzureDiskAttachedVmStep.doStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(flightWorkingMapMock).put(DeleteAzureDiskFlightUtils.DISK_ATTACHED_VM_ID_KEY, vmId);
  }

  @Test
  void doStep_DiskIsNotAttached() throws InterruptedException {
    setupCommonMocks();
    when(disksMock.getById(anyString())).thenReturn(diskMock);
    StepResult stepResult = getAzureDiskAttachedVmStep.doStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(flightWorkingMapMock, never()).put(any(), any());
  }

  @Test
  void doStep_DiskIsNotFound() throws InterruptedException {
    setupCommonMocks();
    ManagementException resourceNotFoundExceptionMock =
        setupManagementExceptionMock("ResourceNotFound");
    when(disksMock.getById(anyString())).thenThrow(resourceNotFoundExceptionMock);

    StepResult stepResult = getAzureDiskAttachedVmStep.doStep(flightContextMock);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(flightWorkingMapMock, never()).put(any(), any());
  }

  @Test
  void undoStep_Success() throws InterruptedException {
    // always return success
    assertThat(
        getAzureDiskAttachedVmStep.undoStep(flightContextMock),
        equalTo(StepResult.getStepResultSuccess()));
  }

  private void setupCommonMocks() {
    when(controlledAzureDiskResourceMock.getDiskName()).thenReturn(DISK_NAME);
    when(azureCloudContextMock.getAzureSubscriptionId()).thenReturn(AZURE_SUBSCRIPTION_ID);
    when(azureCloudContextMock.getAzureResourceGroupId()).thenReturn(AZURE_RESOURCE_GROUP_ID);
    when(flightWorkingMapMock.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(azureCloudContextMock);
    when(flightContextMock.getWorkingMap()).thenReturn(flightWorkingMapMock);
    when(crlServiceMock.getComputeManager(azureCloudContextMock, azureConfigurationMock))
        .thenReturn(computeManagerMock);
    when(computeManagerMock.disks()).thenReturn(disksMock);
  }

  private ManagementException setupManagementExceptionMock(String code) {
    var mockManagementError = mock(ManagementError.class);
    var mockManagementException = mock(ManagementException.class);
    when(mockManagementError.getCode()).thenReturn(code);
    when(mockManagementException.getValue()).thenReturn(mockManagementError);
    return mockManagementException;
  }
}
