package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
public class DeleteAzureDiskStepUnitTest extends BaseMockitoStrictStubbingTest {

  @Mock
  AzureConfiguration azureConfig;
  @Mock
  CrlService crlService;
  @Mock
  ControlledAzureDiskResource resource;
  @Mock
  AzureCloudContext azureCloudContext;
  @Mock
  ComputeManager computeManager;
  @Mock
  Disks disks;

  @Mock
  FlightMap workingMap;
  @Mock
  FlightContext context;


  @BeforeEach
  void localSetup() {
    // since strict stubbing is enabled, we don't want to do much here,
    // but it's probably safe to say all the tests will need the flight context to return the working map
    when(context.getWorkingMap()).thenReturn(workingMap);
  }

  // this test doesn't necessarily have a lot of value in itself,
  // but it helps ensure we've set up our mocks correctly for other tests
  @Test
  void deletingAzureDiskHappyPath() throws Exception {
    when(resource.getDiskName()).thenReturn("disk-name");
    when(azureCloudContext.getAzureSubscriptionId()).thenReturn("test-sub-id");
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn("test-resource-group-id");
    when(workingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(computeManager.disks()).thenReturn(disks);
    when(crlService.getComputeManager(azureCloudContext, azureConfig)).thenReturn(computeManager);

    var step = new DeleteAzureDiskStep(azureConfig, crlService, resource);

    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
    verify(disks).deleteById(anyString());
  }


  @Test
  void noResourceFoundReturnsSuccess() throws Exception {
    when(resource.getDiskName()).thenReturn("disk-name");
    when(azureCloudContext.getAzureSubscriptionId()).thenReturn("test-sub-id");
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn("test-resource-group-id");
    when(workingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(computeManager.disks()).thenReturn(disks);
    when(crlService.getComputeManager(azureCloudContext, azureConfig)).thenReturn(computeManager);

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(404);
    var error = new ManagementError("NotFound", AzureManagementExceptionUtils.RESOURCE_NOT_FOUND);
    var exception = new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);
    doThrow(exception).when(disks).deleteById(any());

    var step = new DeleteAzureDiskStep(azureConfig, crlService, resource);

    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }


  @Test
  void diskAttachedToVMFails() throws Exception {
    when(resource.getDiskName()).thenReturn("disk-name");
    when(azureCloudContext.getAzureSubscriptionId()).thenReturn("test-sub-id");
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn("test-resource-group-id");
    when(workingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(computeManager.disks()).thenReturn(disks);
    when(crlService.getComputeManager(azureCloudContext, azureConfig)).thenReturn(computeManager);

    var response = mock(HttpResponse.class);
    var error = new ManagementError("OperationNotAllowed", "Disk is attached to VM");
    var exception = new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);
    doThrow(exception).when(disks).deleteById(any());

    var step = new DeleteAzureDiskStep(azureConfig, crlService, resource);

    assertThat(step.doStep(context).getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

}
