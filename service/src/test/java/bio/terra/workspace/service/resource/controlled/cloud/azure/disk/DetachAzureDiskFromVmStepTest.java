package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineDataDisk;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DetachAzureDiskFromVmStepTest {
  final String vmId = "vmId";
  final int diskLun = 0; // disk's logical unit

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
  @Mock private VirtualMachines virtualMachinesMock;
  @Mock private VirtualMachine virtualMachineMock;
  @Mock private VirtualMachineDataDisk virtualMachineDataDiskMock;
  @Mock private VirtualMachine.Update virtualMachineUpdateMock;

  private DetachAzureDiskFromVmStep detachAzureDiskFromVmStep;

  @BeforeEach
  void setup() {
    detachAzureDiskFromVmStep =
        new DetachAzureDiskFromVmStep(
            azureConfigurationMock, crlServiceMock, controlledAzureDiskResourceMock);
    when(controlledAzureDiskResourceMock.getDiskName()).thenReturn(DISK_NAME);
  }

  @Test
  void doStep_DiskIsAttached() throws InterruptedException {
    final Map<Integer, VirtualMachineDataDisk> dataDiskMap =
        Map.of(diskLun, virtualMachineDataDiskMock);
    setupCommonMocks();
    setupAzureMocksForDoStep(true, true, true, dataDiskMap, vmId);
    when(virtualMachineDataDiskMock.name()).thenReturn(DISK_NAME);

    StepResult stepResult = detachAzureDiskFromVmStep.doStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(virtualMachineUpdateMock, times(1)).withoutDataDisk(diskLun);
    verify(virtualMachineUpdateMock, times(1)).apply();
  }

  @Test
  void doStep_DiskIsAttachedButVmNotFound() throws InterruptedException {
    final Map<Integer, VirtualMachineDataDisk> dataDiskMap =
        Map.of(diskLun, virtualMachineDataDiskMock);
    setupCommonMocks();
    setupAzureMocksForDoStep(true, false, false, dataDiskMap, vmId);

    StepResult stepResult = detachAzureDiskFromVmStep.doStep(flightContextMock);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
    verify(disksMock, times(1)).getById(any());
    verify(virtualMachineUpdateMock, never()).withoutDataDisk(diskLun);
    verify(virtualMachineUpdateMock, never()).apply();
  }

  @Test
  void doStep_DiskIsNotAttached() throws InterruptedException {
    setupCommonMocks();
    setupAzureMocksForDoStep(false, false, false, null, null);

    StepResult stepResult = detachAzureDiskFromVmStep.doStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(virtualMachinesMock, never()).getById(any());
    verify(virtualMachineUpdateMock, never()).apply();
  }

  @Test
  void doStep_DiskNameDoesntMatchAnyOfAttachedDisks() throws InterruptedException {
    final Map<Integer, VirtualMachineDataDisk> dataDiskMap =
        Map.of(diskLun, virtualMachineDataDiskMock);
    setupCommonMocks();
    when(virtualMachineDataDiskMock.name()).thenReturn("nonMatchedDiskName");
    setupAzureMocksForDoStep(true, true, false, dataDiskMap, vmId);

    StepResult stepResult = detachAzureDiskFromVmStep.doStep(flightContextMock);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
    verify(virtualMachineUpdateMock, never()).withoutDataDisk(diskLun);
    verify(virtualMachineUpdateMock, never()).apply();
  }

  @Test
  void undoStep_DiskWasInitiallyAttachedToVm() throws InterruptedException {
    setupCommonMocks();
    setupAzureMocksForUndoStep(true);

    StepResult stepResult = detachAzureDiskFromVmStep.undoStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(virtualMachineUpdateMock, times(1)).withExistingDataDisk(any());
    verify(virtualMachineUpdateMock, times(1)).apply();
  }

  @Test
  void undoStep_DiskWasNotInitiallyAttachedToVm() throws InterruptedException {
    setupCommonMocks();
    setupAzureMocksForUndoStep(false);

    StepResult stepResult = detachAzureDiskFromVmStep.undoStep(flightContextMock);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(virtualMachineUpdateMock, never()).withExistingDataDisk(any());
    verify(virtualMachineUpdateMock, never()).apply();
  }

  private void setupCommonMocks() {
    when(azureCloudContextMock.getAzureSubscriptionId()).thenReturn(AZURE_SUBSCRIPTION_ID);
    when(azureCloudContextMock.getAzureResourceGroupId()).thenReturn(AZURE_RESOURCE_GROUP_ID);
    when(flightWorkingMapMock.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(azureCloudContextMock);
    when(flightContextMock.getWorkingMap()).thenReturn(flightWorkingMapMock);
    when(crlServiceMock.getComputeManager(azureCloudContextMock, azureConfigurationMock))
        .thenReturn(computeManagerMock);
  }

  private void setupAzureMocksForDoStep(
      boolean isDiskAttachedToVm,
      boolean isVmFound,
      boolean diskMatchesExistingVmDisks,
      Map<Integer, VirtualMachineDataDisk> dataDiskMap,
      String vmId) {
    when(computeManagerMock.disks()).thenReturn(disksMock);
    when(disksMock.getById(anyString())).thenReturn(diskMock);
    when(diskMock.isAttachedToVirtualMachine()).thenReturn(isDiskAttachedToVm);
    if (isDiskAttachedToVm) {
      when(diskMock.virtualMachineId()).thenReturn(vmId);
      if (isVmFound) {
        when(virtualMachineMock.dataDisks()).thenReturn(dataDiskMap);
        when(virtualMachinesMock.getById(vmId)).thenReturn(virtualMachineMock);
        when(computeManagerMock.virtualMachines()).thenReturn(virtualMachinesMock);
        if (diskMatchesExistingVmDisks) {
          when(virtualMachineMock.update()).thenReturn(virtualMachineUpdateMock);
          when(virtualMachineUpdateMock.withoutDataDisk(anyInt()))
              .thenReturn(virtualMachineUpdateMock);
        }
      } else {
        when(computeManagerMock.virtualMachines()).thenReturn(virtualMachinesMock);
        ManagementException resourceNotFoundExceptionMock =
            setupManagementExceptionMock("ResourceNotFound");
        when(virtualMachinesMock.getById(vmId)).thenThrow(resourceNotFoundExceptionMock);
      }
    }
  }

  private void setupAzureMocksForUndoStep(boolean diskWasInitiallyAttached) {
    if (diskWasInitiallyAttached) {
      when(virtualMachineMock.update()).thenReturn(virtualMachineUpdateMock);
      when(computeManagerMock.virtualMachines()).thenReturn(virtualMachinesMock);
      when(virtualMachinesMock.getById(vmId)).thenReturn(virtualMachineMock);
    }
    when(computeManagerMock.disks()).thenReturn(disksMock);
    when(flightWorkingMapMock.get(DeleteAzureDiskFlightUtils.DISK_ATTACHED_VM_ID_KEY, String.class))
        .thenReturn(diskWasInitiallyAttached ? vmId : null);
    when(disksMock.getById(anyString())).thenReturn(diskMock);
  }

  private ManagementException setupManagementExceptionMock(String code) {
    var mockManagementError = mock(ManagementError.class);
    var mockManagementException = mock(ManagementException.class);
    when(mockManagementError.getCode()).thenReturn(code);
    when(mockManagementException.getValue()).thenReturn(mockManagementError);
    return mockManagementException;
  }
}
