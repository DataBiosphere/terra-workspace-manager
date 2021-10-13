package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import bio.terra.cloudres.azure.resourcemanager.compute.data.CreatePublicIpRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureIpStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.IpAllocationMethod;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.network.models.PublicIpAddresses;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class CreateAzureIpStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private PublicIpAddress mockPublicIpAddress;
  @Mock private ComputeManager mockComputeManager;
  @Mock private NetworkManager mockNetworkManager;
  @Mock private PublicIpAddresses mockPublicIpAddresses;
  @Mock private PublicIpAddress.DefinitionStages.Blank mockIpStage1;
  @Mock private PublicIpAddress.DefinitionStages.WithGroup mockIpStage2;
  @Mock private PublicIpAddress.DefinitionStages.WithCreate mockIpStage3;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    // PublicIpAddresses mocks
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.networkManager()).thenReturn(mockNetworkManager);
    when(mockNetworkManager.publicIpAddresses()).thenReturn(mockPublicIpAddresses);

    // creation mocks
    when(mockPublicIpAddresses.define(anyString())).thenReturn(mockIpStage1);
    when(mockIpStage1.withRegion(any(Region.class))).thenReturn(mockIpStage2);
    when(mockIpStage2.withExistingResourceGroup(anyString())).thenReturn(mockIpStage3);
    when(mockIpStage3.withDynamicIP()).thenReturn(mockIpStage3);
    when(mockIpStage3.withTag(anyString(), anyString())).thenReturn(mockIpStage3);
    when(mockIpStage3.create(any(Context.class))).thenReturn(mockPublicIpAddress);

    // deletion mocks
    doNothing().when(mockPublicIpAddresses).deleteByResourceGroup(anyString(), anyString());
  }

  @Test
  void createIp() throws InterruptedException {
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    CreateAzureIpStep createAzureIpStep =
        new CreateAzureIpStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureIp(
                creationParameters.getName(), creationParameters.getRegion()));

    final StepResult stepResult = createAzureIpStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockIpStage3).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreatePublicIpRequestData> publicIpRequestDataOpt =
        context.getValues().values().stream()
            .filter(CreatePublicIpRequestData.class::isInstance)
            .map(CreatePublicIpRequestData.class::cast)
            .findFirst();

    CreatePublicIpRequestData expected =
        CreatePublicIpRequestData.builder()
            .setName(creationParameters.getName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setIpAllocationMethod(IpAllocationMethod.DYNAMIC)
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(publicIpRequestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  void deleteIp() throws InterruptedException {
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    CreateAzureIpStep createAzureIpStep =
        new CreateAzureIpStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureIp(
                creationParameters.getName(), creationParameters.getRegion()));

    final StepResult stepResult = createAzureIpStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockPublicIpAddresses)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }
}
