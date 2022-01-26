package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.NetworkSecurityGroups;
import com.azure.resourcemanager.network.models.NetworkSecurityRule;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.network.models.PublicIpAddresses;
import com.azure.resourcemanager.network.models.SecurityRuleProtocol;
import com.azure.resourcemanager.network.models.Subnet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class CreateAzureVmStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";
  private static final String STUB_DISK_NAME = "stub-disk-name";
  private static final String STUB_IP_NAME = "stub-disk-name";
  private static final String STUB_NETWORK_NAME = "stub-network-name";
  private static final String STUB_SUBNET_NAME = "stub-subnet-name";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private ResourceDao mockResourceDao;
  @Mock private VirtualMachines mockVms;
  @Mock private VirtualMachine mockVm;

  @Mock private VirtualMachine.DefinitionStages.Blank mockVmStage1;
  @Mock private VirtualMachine.DefinitionStages.WithGroup mockVmStage2;
  @Mock private VirtualMachine.DefinitionStages.WithNetwork mockVmStage3;
  @Mock private VirtualMachine.DefinitionStages.WithSubnet mockVmStage4;
  @Mock private VirtualMachine.DefinitionStages.WithPrivateIP mockVmStage5;
  @Mock private VirtualMachine.DefinitionStages.WithPublicIPAddress mockVmStage6;
  @Mock private VirtualMachine.DefinitionStages.WithProximityPlacementGroup mockVmStage7;

  @Mock private VirtualMachine.DefinitionStages.WithLinuxCreateManaged mockVmStage10;
  @Mock private VirtualMachine.DefinitionStages.WithManagedCreate mockVmStage11;
  @Mock private VirtualMachine.DefinitionStages.WithCreate mockVmStage11a;
  @Mock private VirtualMachine.DefinitionStages.WithCreate mockVmStage12;
  @Mock private ManagementException mockException;

  @Mock private Disks mockDisks;
  @Mock private Disk mockDisk;

  @Mock private NetworkManager mockNetworkManager;
  @Mock private PublicIpAddresses mockPublicIpAddresses;
  @Mock private PublicIpAddress mockPublicIpAddress;
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

  @Mock private WsmResource mockWsmResource;
  @Mock private ControlledResource mockControlledResource;
  @Mock private ControlledAzureIpResource mockAzureIpResource;
  @Mock private ControlledAzureDiskResource mockAzureDiskResource;
  @Mock private ControlledAzureNetworkResource mockAzureNetworkResource;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    when(mockCrlService.getComputeManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockComputeManager);
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);

    when(mockComputeManager.virtualMachines()).thenReturn(mockVms);

    // get disk mocks
    when(mockComputeManager.disks()).thenReturn(mockDisks);
    when(mockDisks.getByResourceGroup(anyString(), anyString())).thenReturn(mockDisk);

    // get ip mocks
    when(mockComputeManager.networkManager()).thenReturn(mockNetworkManager);
    when(mockNetworkManager.publicIpAddresses()).thenReturn(mockPublicIpAddresses);
    when(mockPublicIpAddresses.getByResourceGroup(anyString(), anyString()))
        .thenReturn(mockPublicIpAddress);

    // get network mocks
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

    // Creation vm stages mocks
    when(mockVms.define(anyString())).thenReturn(mockVmStage1);
    when(mockVmStage1.withRegion(anyString())).thenReturn(mockVmStage2);
    when(mockVmStage2.withExistingResourceGroup(anyString())).thenReturn(mockVmStage3);
    when(mockVmStage3.withExistingPrimaryNetwork(any(Network.class))).thenReturn(mockVmStage4);
    when(mockVmStage4.withSubnet(anyString())).thenReturn(mockVmStage5);
    when(mockVmStage5.withPrimaryPrivateIPAddressDynamic()).thenReturn(mockVmStage6);
    when(mockVmStage6.withExistingPrimaryPublicIPAddress(any(PublicIpAddress.class)))
        .thenReturn(mockVmStage7);
    when(mockVmStage7.withSpecializedLinuxCustomImage(anyString())).thenReturn(mockVmStage10);
    when(mockVmStage10.withExistingDataDisk(any(Disk.class))).thenReturn(mockVmStage11);
    when(mockVmStage11.withTag(anyString(), anyString())).thenReturn(mockVmStage11a);
    when(mockVmStage11a.withTag(anyString(), anyString())).thenReturn(mockVmStage12);
    when(mockVmStage12.withSize(any(VirtualMachineSizeTypes.class))).thenReturn(mockVmStage12);
    when(mockVmStage12.create(any(Context.class))).thenReturn(mockVm);

    // Resource dao mocks
    when(mockResourceDao.getResource(any(UUID.class), any(UUID.class))).thenReturn(mockWsmResource);
    when(mockWsmResource.castToControlledResource()).thenReturn(mockControlledResource);
    when(mockControlledResource.castToAzureDiskResource()).thenReturn(mockAzureDiskResource);
    when(mockControlledResource.castToAzureIpResource()).thenReturn(mockAzureIpResource);
    when(mockControlledResource.castToAzureNetworkResource()).thenReturn(mockAzureNetworkResource);

    // Resource mocks
    when(mockAzureDiskResource.getDiskName()).thenReturn(STUB_DISK_NAME);
    when(mockAzureIpResource.getIpName()).thenReturn(STUB_IP_NAME);
    when(mockAzureNetworkResource.getNetworkName()).thenReturn(STUB_NETWORK_NAME);
    when(mockAzureNetworkResource.getSubnetName()).thenReturn(STUB_SUBNET_NAME);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));
  }

  @Test
  void createVm() throws InterruptedException {
    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    final StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockVmStage12).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateVirtualMachineRequestData> requestDataOpt =
        context.getValues().values().stream()
            .filter(CreateVirtualMachineRequestData.class::isInstance)
            .map(CreateVirtualMachineRequestData.class::cast)
            .findFirst();

    CreateVirtualMachineRequestData expected =
        CreateVirtualMachineRequestData.builder()
            .setName(creationParameters.getName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setTenantId(mockAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(mockAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .setDisk(mockDisk)
            .setNetwork(mockNetwork)
            .setSubnetName(STUB_SUBNET_NAME)
            .setPublicIpAddress(mockPublicIpAddress)
            .setImage(ControlledResourceFixtures.getAzureVmCreationParameters().getVmImageUri())
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createVm_alreadyExists() throws InterruptedException {
    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    // Stub creation to throw Conflict exception.
    when(mockVmStage12.create(any(Context.class))).thenThrow(mockException);

    final StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteVm() throws InterruptedException {
    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    final StepResult stepResult = createAzureVmStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockVms)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }
}
