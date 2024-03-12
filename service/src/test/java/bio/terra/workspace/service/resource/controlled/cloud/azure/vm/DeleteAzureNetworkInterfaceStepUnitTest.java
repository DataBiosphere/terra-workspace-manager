package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.NetworkInterfaces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class DeleteAzureNetworkInterfaceStepUnitTest {

  @Mock private AzureConfiguration azureConfiguration;
  @Mock private CrlService crlService;
  @Mock ControlledAzureVmResource resource;

  @Mock AzureCloudContext azureCloudContext;
  @Mock ComputeManager computeManager;

  @Mock NetworkManager networkManager;
  @Mock NetworkInterfaces networkInterfaces;

  @Mock FlightMap workingMap;
  @Mock FlightContext context;

  @BeforeEach
  void localSetup() {
    when(context.getWorkingMap()).thenReturn(workingMap);
    when(workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(crlService.getComputeManager(azureCloudContext, azureConfiguration))
        .thenReturn(computeManager);
    when(computeManager.networkManager()).thenReturn(networkManager);
    when(networkManager.networkInterfaces()).thenReturn(networkInterfaces);
  }

  @Test
  void happyPathDeletingNetworkInterface() throws Exception {
    var vmName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", vmName);
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    when(resource.getVmName()).thenReturn(vmName);
    doNothing()
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step = new DeleteAzureNetworkInterfaceStep(azureConfiguration, crlService, resource);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }

  @Test
  void stepSucceedsWhenResourceIsAlreadyGone() throws Exception {
    var vmName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", vmName);
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    when(resource.getVmName()).thenReturn(vmName);

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(404);
    var error = new ManagementError(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, "NotFound");
    var exception =
        new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);

    doThrow(exception)
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step = new DeleteAzureNetworkInterfaceStep(azureConfiguration, crlService, resource);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));

    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }

  @Test
  void stepSucceedsWhenSubscriptionIsGone() throws Exception {
    var vmName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", vmName);
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    when(resource.getVmName()).thenReturn(vmName);

    var response = mock(HttpResponse.class);
    var error = new ManagementError("SubscriptionNotFound", "NotFound");
    var exception = new ManagementException("SubscriptionNotFound", response, error);

    doThrow(exception)
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step = new DeleteAzureNetworkInterfaceStep(azureConfiguration, crlService, resource);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));

    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }
}
