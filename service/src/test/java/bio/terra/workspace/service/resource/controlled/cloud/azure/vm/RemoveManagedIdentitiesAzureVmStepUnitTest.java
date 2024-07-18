package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class RemoveManagedIdentitiesAzureVmStepUnitTest {

  @Test
  public void removeManagedIdentitiesAzureFromVm_returnsSuccessOnMissingMRG()
      throws InterruptedException {
    var flightContext = mock(FlightContext.class);
    var workingMap = mock(FlightMap.class);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    var cloudContext = mock(AzureCloudContext.class);
    when(cloudContext.getAzureResourceGroupId()).thenReturn("resource-group-id");
    when(workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(cloudContext);

    var error = new ManagementError("SubscriptionNotFound", "some message about the subscription");
    var virtualMachines = mock(VirtualMachines.class);
    doThrow(new ManagementException("some message", mock(HttpResponse.class), error))
        .when(virtualMachines)
        .getByResourceGroup(any(), any());
    var computeManager = mock(ComputeManager.class);
    when(computeManager.virtualMachines()).thenReturn(virtualMachines);

    var crlService = mock(CrlService.class);
    when(crlService.getComputeManager(any(), any())).thenReturn(computeManager);

    var resource = mock(ControlledAzureVmResource.class);
    when(resource.getVmName()).thenReturn("vm-name");
    var step = new RemoveManagedIdentitiesAzureVmStep(mock(), crlService, resource);
    step.doStep(flightContext);
    StepResult stepResult = step.doStep(flightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }
}
