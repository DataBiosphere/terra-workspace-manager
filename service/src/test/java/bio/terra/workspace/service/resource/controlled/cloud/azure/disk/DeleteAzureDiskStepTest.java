package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disks;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DeleteAzureDiskStepTest {
  private static final String resourceIdFormat =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/disks/%s";
  private static final String SUBSCRIPTION_ID = "subId";
  private static final String RESOURCE_GROUP_ID = "resGroupId";
  private static final String DISK_NAME = "vmDisk";

  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private CrlService mockCrlService;
  @Mock private ControlledAzureDiskResource mockDiskResource;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private Disks mockDisks;
  @Mock private ManagementException mockManagementException;
  @Mock private StepResult mockStepResult;
  @Captor private ArgumentCaptor<String> diskResourceIdCaptor;

  private DeleteAzureDiskStep testStep;

  @BeforeEach
  void setup() {
    testStep = new DeleteAzureDiskStep(mockAzureConfig, mockCrlService, mockDiskResource);
  }

  @Test
  void deleteDisk() throws InterruptedException {
    setupBaseMocks();

    StepResult result = testStep.doStep(mockFlightContext);

    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
    verify(mockDisks, times(1)).deleteById(diskResourceIdCaptor.capture());
    assertThat(
        diskResourceIdCaptor.getValue(),
        equalTo(String.format(resourceIdFormat, SUBSCRIPTION_ID, RESOURCE_GROUP_ID, DISK_NAME)));
  }

  @Test
  void deleteDisk_diskIsAttachedToExistingVm() throws InterruptedException {
    setupBaseMocks();
    setupExceptionMock();
    doThrow(mockManagementException).when(mockDisks).deleteById(anyString());

    StepResult result = testStep.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    verify(mockDisks, times(1)).deleteById(diskResourceIdCaptor.capture());
    assertThat(
        diskResourceIdCaptor.getValue(),
        equalTo(String.format(resourceIdFormat, SUBSCRIPTION_ID, RESOURCE_GROUP_ID, DISK_NAME)));
  }

  @Test
  void deleteDisk_noResourceFoundReturnsSuccess() throws Exception {
    setupBaseMocks();
    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(404);
    var error = new ManagementError("NotFound", AzureManagementExceptionUtils.RESOURCE_NOT_FOUND);
    var exception =
        new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);

    doThrow(exception).when(mockDisks).deleteById(any());

    assertThat(testStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void undoStep_diskIsAttachedToExistingVm() throws InterruptedException {
    setupExceptionMock();
    when(mockStepResult.getStepStatus()).thenReturn(StepStatus.STEP_RESULT_FAILURE_FATAL);
    when(mockStepResult.getException()).thenReturn(Optional.of(mockManagementException));
    when(mockFlightContext.getResult()).thenReturn(mockStepResult);

    StepResult result = testStep.undoStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  private void setupExceptionMock() {
    var mockManagementError = mock(ManagementError.class);
    when(mockManagementError.getCode()).thenReturn("OperationNotAllowed");
    when(mockManagementError.getMessage()).thenReturn("Disk is attached to VM");
    when(mockManagementException.getValue()).thenReturn(mockManagementError);
  }

  private void setupBaseMocks() {
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(SUBSCRIPTION_ID);
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(RESOURCE_GROUP_ID);
    when(mockDiskResource.getDiskName()).thenReturn(DISK_NAME);
    when(mockWorkingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockComputeManager.disks()).thenReturn(mockDisks);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
  }
}
