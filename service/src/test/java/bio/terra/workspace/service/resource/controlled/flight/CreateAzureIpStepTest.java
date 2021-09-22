package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.AzureState;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureIpStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.network.models.PublicIpAddresses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class CreateAzureIpStepTest extends BaseAzureTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private PublicIpAddress mockPublicIpAddress;
  @Mock private ComputeManager mockComputeManager;
  @Mock private NetworkManager mockNetworkManager;
  @Mock private PublicIpAddresses mockPublicIpAddresses;
  @Mock private PublicIpAddress.DefinitionStages.Blank mockIpStage1;
  @Mock private PublicIpAddress.DefinitionStages.WithGroup mockIpStage2;
  @Mock private PublicIpAddress.DefinitionStages.WithCreate mockIpStage3;
  private static final String STUB_STRING_RETURN = "stubbed-return";
  @Mock private Region mockRegion;

  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @Autowired private AzureState azureState;

  @BeforeEach
  public void setup() {
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.networkManager()).thenReturn(mockNetworkManager);
    when(mockNetworkManager.publicIpAddresses()).thenReturn(mockPublicIpAddresses);
    when(mockPublicIpAddresses.define(any(String.class))).thenReturn(mockIpStage1);
    when(mockIpStage1.withRegion(any(Region.class))).thenReturn(mockIpStage2);
    when(mockIpStage2.withExistingResourceGroup(any(String.class))).thenReturn(mockIpStage3);
    when(mockIpStage3.withDynamicIP()).thenReturn(mockIpStage3);
    when(mockIpStage3.withTag(any(String.class), any(String.class))).thenReturn(mockIpStage3);
    when(mockIpStage3.create(any(Context.class))).thenReturn(mockPublicIpAddress);
  }

  @Test
  void testCreatesIp() throws InterruptedException {
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    CreateAzureIpStep createAzureIpStep =
        new CreateAzureIpStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureIp(creationParameters.getName()),
            mockWorkspaceService);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult stepResult = createAzureIpStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }
}
