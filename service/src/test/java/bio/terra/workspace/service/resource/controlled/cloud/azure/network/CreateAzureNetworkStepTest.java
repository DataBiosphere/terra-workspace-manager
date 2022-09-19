package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateNetworkRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.NetworkSecurityGroups;
import com.azure.resourcemanager.network.models.NetworkSecurityRule;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.SecurityRuleProtocol;
import com.azure.resourcemanager.network.models.Subnet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;


public class CreateAzureNetworkStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private ComputeManager mockComputeManager;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private NetworkManager mockNetworkManager;
  @Mock private Network mockNetwork;
  @Mock private Networks mockNetworks;

  @Mock private NetworkSecurityGroup mockNsg;
  @Mock private NetworkSecurityGroups mockNsgs;
  @Mock private NetworkSecurityGroup.DefinitionStages.Blank mockNetworkStage1;
  @Mock private NetworkSecurityGroup.DefinitionStages.WithGroup mockNetworkStage1a;
  @Mock private NetworkSecurityGroup.DefinitionStages.WithCreate mockNetworkStage2;

  @Mock
  private NetworkSecurityRule.DefinitionStages.Blank<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage3;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithSourceAddressOrSecurityGroup<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage4;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithSourcePort<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage5;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithDestinationAddressOrSecurityGroup<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage6;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithDestinationPort<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage7;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithProtocol<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage8;

  @Mock
  private NetworkSecurityRule.DefinitionStages.WithAttach<
          NetworkSecurityGroup.DefinitionStages.WithCreate>
      mockNetworkStage9;

  @Mock private Network.DefinitionStages.Blank mockNetworkStage10;
  @Mock private Network.DefinitionStages.WithGroup mockNetworkStage11;
  @Mock private Network.DefinitionStages.WithCreate mockNetworkStage12;
  @Mock private Network.DefinitionStages.WithCreateAndSubnet mockNetworkStage13;

  @Mock
  private Subnet.DefinitionStages.Blank<Network.DefinitionStages.WithCreateAndSubnet>
      mockNetworkStage14;

  @Mock
  private Subnet.DefinitionStages.WithAttach<Network.DefinitionStages.WithCreateAndSubnet>
      mockNetworkStage15;

  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.networkManager()).thenReturn(mockNetworkManager);

    when(mockNetworkManager.networks()).thenReturn(mockNetworks);
    when(mockNetworks.getByResourceGroup(anyString(), anyString())).thenReturn(mockNetwork);

    // create network security group mocks
    when(mockNetworkManager.networkSecurityGroups()).thenReturn(mockNsgs);
    when(mockNsgs.define(anyString())).thenReturn(mockNetworkStage1);
    when(mockNetworkStage1.withRegion(anyString())).thenReturn(mockNetworkStage1a);
    when(mockNetworkStage1a.withExistingResourceGroup(anyString())).thenReturn(mockNetworkStage2);
    when(mockNetworkStage2.withTag(anyString(), anyString())).thenReturn(mockNetworkStage2);
    when(mockNetworkStage2.defineRule(anyString())).thenReturn(mockNetworkStage3);
    when(mockNetworkStage3.allowInbound()).thenReturn(mockNetworkStage4);
    when(mockNetworkStage3.denyOutbound()).thenReturn(mockNetworkStage4);
    when(mockNetworkStage4.fromAddress(anyString())).thenReturn(mockNetworkStage5);
    when(mockNetworkStage4.fromAnyAddress()).thenReturn(mockNetworkStage5);
    when(mockNetworkStage5.fromAnyPort()).thenReturn(mockNetworkStage6);
    when(mockNetworkStage6.toAnyAddress()).thenReturn(mockNetworkStage7);
    when(mockNetworkStage6.toAddress(anyString())).thenReturn(mockNetworkStage7);
    when(mockNetworkStage7.toPort(anyInt())).thenReturn(mockNetworkStage8);
    when(mockNetworkStage7.toAnyPort()).thenReturn(mockNetworkStage8);
    when(mockNetworkStage8.withProtocol(any(SecurityRuleProtocol.class)))
        .thenReturn(mockNetworkStage9);
    when(mockNetworkStage8.withAnyProtocol()).thenReturn(mockNetworkStage9);
    when(mockNetworkStage9.attach()).thenReturn(mockNetworkStage2);
    when(mockNetworkStage2.create(any(Context.class))).thenReturn(mockNsg);

    // create network mocks
    when(mockNetworks.define(anyString())).thenReturn(mockNetworkStage10);
    when(mockNetworkStage10.withRegion(anyString())).thenReturn(mockNetworkStage11);
    when(mockNetworkStage11.withExistingResourceGroup(anyString())).thenReturn(mockNetworkStage12);
    when(mockNetworkStage12.withTag(anyString(), anyString())).thenReturn(mockNetworkStage12);
    when(mockNetworkStage12.withAddressSpace(anyString())).thenReturn(mockNetworkStage13);
    when(mockNetworkStage13.defineSubnet(anyString())).thenReturn(mockNetworkStage14);
    when(mockNetworkStage14.withAddressPrefix(anyString())).thenReturn(mockNetworkStage15);
    when(mockNetworkStage15.withExistingNetworkSecurityGroup(any(NetworkSecurityGroup.class)))
        .thenReturn(mockNetworkStage15);
    when(mockNetworkStage15.attach()).thenReturn(mockNetworkStage13);
    when(mockNetworkStage13.create(any(Context.class))).thenReturn(mockNetwork);

    // Deletion mocks

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  void createNetwork() throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    var createAzureNetworkStep =
        new CreateAzureNetworkStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureNetwork(creationParameters));

    final StepResult stepResult = createAzureNetworkStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockNetworkStage13).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateNetworkRequestData> requestDataOpt =
        context.getValues().values().stream()
            .filter(CreateNetworkRequestData.class::isInstance)
            .map(CreateNetworkRequestData.class::cast)
            .findFirst();

    CreateNetworkRequestData expected =
        CreateNetworkRequestData.builder()
            .setName(creationParameters.getName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setSubnetName(creationParameters.getSubnetName())
            .setAddressSpaceCidr(creationParameters.getAddressSpaceCidr())
            .setNetworkSecurityGroup(mockNsg)
            .setAddressPrefix(creationParameters.getSubnetAddressCidr())
            .setTenantId(mockAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(mockAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createNetwork_alreadyExists() throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    CreateAzureNetworkStep createAzureNetworkStep =
        new CreateAzureNetworkStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureNetwork(creationParameters));

    // Stub creation to throw Conflict exception.
    when(mockNetworkStage13.create(any(Context.class))).thenThrow(mockException);

    final StepResult stepResult = createAzureNetworkStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteNetwork() throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    CreateAzureNetworkStep createAzureNetworkStep =
        new CreateAzureNetworkStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureNetwork(creationParameters));

    final StepResult stepResult = createAzureNetworkStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockNetworks)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }
}
