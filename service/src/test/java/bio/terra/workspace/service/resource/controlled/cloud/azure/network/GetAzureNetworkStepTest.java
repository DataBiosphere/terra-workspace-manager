package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class GetAzureNetworkStepTest extends BaseAzureUnitTest {
  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private NetworkManager mockNetworkManager;
  @Mock private Network mockNetwork;
  @Mock private Networks mockNetworks;
  @Mock private ComputeManager mockComputeManager;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  @BeforeEach
  public void setup() {
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.networkManager()).thenReturn(mockNetworkManager);
    when(mockNetworkManager.networks()).thenReturn(mockNetworks);
    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  public void getNetwork_doesNotExist() throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParams =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    GetAzureNetworkStep step =
        new GetAzureNetworkStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureNetwork(creationParams));

    when(mockNetworks.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParams.getName()))
        .thenThrow(mockException);

    final StepResult stepResult = step.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getNetwork_alreadyExists() throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParams =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    GetAzureNetworkStep step =
        new GetAzureNetworkStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureNetwork(creationParams));

    when(mockNetworks.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParams.getName()))
        .thenReturn(mockNetwork);

    final StepResult stepResult = step.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
