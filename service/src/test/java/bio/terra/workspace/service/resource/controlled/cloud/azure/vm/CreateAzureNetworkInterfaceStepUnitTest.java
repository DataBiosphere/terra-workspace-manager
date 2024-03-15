package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.NetworkInterfaces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class CreateAzureNetworkInterfaceStepUnitTest {

  @Mock FlightContext context;
  @Mock FlightMap workingMap;
  @Mock AzureCloudContext azureCloudContext;
  @Mock AzureConfiguration azureConfiguration;

  @Mock CrlService crlService;
  @Mock ComputeManager computeManager;
  @Mock NetworkInterfaces networkInterfaces;
  @Mock NetworkManager networkManager;

  @Mock ControlledAzureVmResource resource;
  @Mock SamService samService;
  @Mock WorkspaceService mockWorkspaceService;
  @Mock ResourceDao resourceDao;
  @Mock LandingZoneApiDispatch landingZoneApiDispatch;

  @BeforeEach
  void setup() {
    when(workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(context.getWorkingMap()).thenReturn(workingMap);
    when(crlService.getComputeManager(azureCloudContext, azureConfiguration))
        .thenReturn(computeManager);
    when(computeManager.networkManager()).thenReturn(networkManager);
    when(networkManager.networkInterfaces()).thenReturn(networkInterfaces);
  }

  @Test
  void undo_HappyPath() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var resourceName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", resourceName);
    when(workingMap.get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class))
        .thenReturn(networkInterfaceName);

    doNothing()
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step =
        new CreateAzureNetworkInterfaceStep(
            azureConfiguration,
            crlService,
            resource,
            resourceDao,
            landingZoneApiDispatch,
            samService,
            mockWorkspaceService);
    assertThat(step.undoStep(context), equalTo(StepResult.getStepResultSuccess()));

    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }

  @Test
  void undo_SucceedsWhenResourceNotFound() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var resourceName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", resourceName);
    when(workingMap.get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class))
        .thenReturn(networkInterfaceName);
    var response = mock(HttpResponse.class);
    var error = new ManagementError(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, "NotFound");
    var exception =
        new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);
    doThrow(exception)
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step =
        new CreateAzureNetworkInterfaceStep(
            azureConfiguration,
            crlService,
            resource,
            resourceDao,
            landingZoneApiDispatch,
            samService,
            mockWorkspaceService);

    assertThat(step.undoStep(context), equalTo(StepResult.getStepResultSuccess()));
    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }

  @Disabled(
      "AzureVmHelper.deleteNetworkInterface has a 180 second sleep when the NIC_RESERVED_FOR_ANOTHER_VM is thrown")
  @Test
  void undo_RetriesWhenNetworkIsReserved() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var resourceName = "test-vm-name";
    var networkInterfaceName = String.format("nic-%s", resourceName);
    when(workingMap.get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class))
        .thenReturn(networkInterfaceName);
    var response = mock(HttpResponse.class);
    var error =
        new ManagementError(AzureManagementExceptionUtils.NIC_RESERVED_FOR_ANOTHER_VM, "NotFound");
    var exception =
        new ManagementException(
            AzureManagementExceptionUtils.NIC_RESERVED_FOR_ANOTHER_VM, response, error);

    doThrow(exception)
        .when(networkInterfaces)
        .deleteByResourceGroup(resourceGroupId, networkInterfaceName);

    var step =
        new CreateAzureNetworkInterfaceStep(
            azureConfiguration,
            crlService,
            resource,
            resourceDao,
            landingZoneApiDispatch,
            samService,
            mockWorkspaceService);

    assertThat(
        step.undoStep(context).getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
    verify(networkInterfaces).deleteByResourceGroup(resourceGroupId, networkInterfaceName);
  }
}
