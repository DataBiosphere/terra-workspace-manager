package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.NetworkInterfaces;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.Mockito;

public class DeleteAzureNetworkInterfaceStepUnitTest extends BaseMockitoStrictStubbingTest {

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
    Mockito.lenient().when(context.getWorkingMap()).thenReturn(workingMap);
  }
  /*
  computeManager
            .networkManager()
            .networkInterfaces()
            .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), networkInterfaceName)
   */

}
