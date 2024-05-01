package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import com.azure.resourcemanager.compute.models.ImageReference;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineExtension;
import com.azure.resourcemanager.compute.models.VirtualMachinePriorityTypes;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.network.models.NetworkInterfaces;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.NetworkSecurityGroups;
import com.azure.resourcemanager.network.models.NetworkSecurityRule;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.NicIpConfiguration;
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

public class CreateAzureVmStepTest extends BaseAzureSpringBootUnitTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";
  private static final String STUB_DISK_NAME = "stub-disk-name";
  private static final String STUB_SUBNET_NAME = "stub-subnet-name";
  private static final String STUB_NETWORK_INTERFACE_NAME = "nic-name";

  private static final String STUB_NETWORK_REGION_NAME = "westcentralus";

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
  @Mock private VirtualMachine.DefinitionStages.WithProximityPlacementGroup mockVmStage7;

  @Mock private VirtualMachine.DefinitionStages.WithLinuxCreateManagedOrUnmanaged mockVmStage10;
  @Mock private VirtualMachine.DefinitionStages.WithManagedCreate mockVmStage11;
  @Mock private VirtualMachine.DefinitionStages.WithCreate mockVmStage12;

  @Mock
  private VirtualMachineExtension.DefinitionStages.Blank<VirtualMachine.DefinitionStages.WithCreate>
      mockVmStage13;

  @Mock
  private VirtualMachineExtension.DefinitionStages.WithType<
          VirtualMachine.DefinitionStages.WithCreate>
      mockVmStage14;

  @Mock
  private VirtualMachineExtension.DefinitionStages.WithVersion<
          VirtualMachine.DefinitionStages.WithCreate>
      mockVmStage15;

  @Mock
  private VirtualMachineExtension.DefinitionStages.WithAttach<
          VirtualMachine.DefinitionStages.WithCreate>
      mockVmStage16;

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
  @Mock private NetworkInterfaces mockNis;

  @Mock private NetworkInterface mockNi;

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
  private VirtualMachine.DefinitionStages.WithLinuxRootUsernameManagedOrUnmanaged mockVmStage7a;

  @Mock
  private VirtualMachine.DefinitionStages.WithLinuxRootPasswordOrPublicKeyManagedOrUnmanaged
      mockVmStage7b;

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
  @Mock private ControlledAzureDiskResource mockAzureDiskResource;
  @Mock private FlightMap mockWorkingMap;

  private final ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @Mock private NicIpConfiguration mockIpConfiguration;

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
    when(mockNetworkManager.networkInterfaces()).thenReturn(mockNis);
    when(mockNis.getByResourceGroup(anyString(), anyString())).thenReturn(mockNi);
    when(mockNi.primaryIPConfiguration()).thenReturn(mockIpConfiguration);
    when(mockIpConfiguration.getNetwork()).thenReturn(mockNetwork);

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

    when(mockVms.define(anyString())).thenReturn(mockVmStage1);
    when(mockVmStage1.withRegion(any(Region.class))).thenReturn(mockVmStage2);
    when(mockVmStage2.withExistingResourceGroup(anyString())).thenReturn(mockVmStage3);
    when(mockVmStage3.withExistingPrimaryNetworkInterface(mockNi)).thenReturn(mockVmStage7);
    when(mockVmStage7.withSpecificLinuxImageVersion(any(ImageReference.class)))
        .thenReturn(mockVmStage7a);
    when(mockVmStage7a.withRootUsername(anyString())).thenReturn(mockVmStage7b);
    when(mockVmStage7b.withRootPassword(anyString())).thenReturn(mockVmStage10);
    when(mockVmStage10.withExistingDataDisk(any(Disk.class))).thenReturn(mockVmStage11);
    when(mockVmStage11.withSize(any(VirtualMachineSizeTypes.class))).thenReturn(mockVmStage12);
    when(mockVmStage12.withPriority(any(VirtualMachinePriorityTypes.class)))
        .thenReturn(mockVmStage12);
    when(mockVmStage12.withTag(anyString(), anyString())).thenReturn(mockVmStage12);
    when(mockVmStage12.withTag(anyString(), anyString())).thenReturn(mockVmStage12);
    when(mockVmStage12.defineNewExtension(anyString())).thenReturn(mockVmStage13);
    when(mockVmStage12.create(any(Context.class))).thenReturn(mockVm);
    when(mockVmStage13.withPublisher(anyString())).thenReturn(mockVmStage14);
    when(mockVmStage14.withType(anyString())).thenReturn(mockVmStage15);
    when(mockVmStage15.withVersion(anyString())).thenReturn(mockVmStage16);
    when(mockVmStage16.withPublicSettings(any())).thenReturn(mockVmStage16);
    when(mockVmStage16.withProtectedSettings(any())).thenReturn(mockVmStage16);
    when(mockVmStage16.withTags(any())).thenReturn(mockVmStage16);
    when(mockVmStage16.withoutMinorVersionAutoUpgrade()).thenReturn(mockVmStage16);
    when(mockVmStage16.attach()).thenReturn(mockVmStage12);

    // Resource dao mocks
    when(mockResourceDao.getResource(any(UUID.class), any(UUID.class))).thenReturn(mockWsmResource);
    when(mockWsmResource.castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK))
        .thenReturn(mockAzureDiskResource);

    // Resource mocks
    when(mockAzureDiskResource.getDiskName()).thenReturn(STUB_DISK_NAME);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockWorkingMap.containsKey(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY))
        .thenReturn(true);
    when(mockWorkingMap.get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class))
        .thenReturn(STUB_NETWORK_INTERFACE_NAME);
    when(mockWorkingMap.get(AzureVmHelper.WORKING_MAP_SUBNET_NAME, String.class))
        .thenReturn(STUB_SUBNET_NAME);
    when(mockWorkingMap.get(CREATE_RESOURCE_REGION, String.class))
        .thenReturn(STUB_NETWORK_REGION_NAME);
  }

  @Test
  void createVm() throws InterruptedException {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    FlightMap creationParametersFlightMap = new FlightMap();
    creationParametersFlightMap.put(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    creationParametersFlightMap.makeImmutable();
    when(mockFlightContext.getInputParameters()).thenReturn(creationParametersFlightMap);

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

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
            .setRegion(Region.fromName(STUB_NETWORK_REGION_NAME))
            .setTenantId(mockAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(mockAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .setDisk(mockDisk)
            .setNetwork(mockNetwork)
            .setSubnetName(STUB_SUBNET_NAME)
            .setImage(
                AzureUtils.getVmImageData(
                    ControlledAzureResourceFixtures.getAzureVmCreationParameters().getVmImage()))
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  void createVm_alreadyExists() throws InterruptedException {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    FlightMap creationParametersFlightMap = new FlightMap();
    creationParametersFlightMap.put(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    creationParametersFlightMap.makeImmutable();
    when(mockFlightContext.getInputParameters()).thenReturn(creationParametersFlightMap);

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    // Stub creation to throw Conflict exception.
    when(mockVmStage12.create(any(Context.class))).thenThrow(mockException);

    StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void createVm_ResourceNotFound() throws InterruptedException {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    FlightMap creationParametersFlightMap = new FlightMap();
    creationParametersFlightMap.put(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    creationParametersFlightMap.makeImmutable();
    when(mockFlightContext.getInputParameters()).thenReturn(creationParametersFlightMap);

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    // throw a not found exception
    var mockResponse = mock(HttpResponse.class);
    when(mockResponse.getStatusCode()).thenReturn(200);
    var notFoundException =
        new ManagementException(
            "error",
            mockResponse,
            new ManagementError(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, "Not found"));
    when(mockVmStage12.create(any(Context.class))).thenThrow(notFoundException);

    StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void createVm_generic4xxException() throws InterruptedException {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    FlightMap creationParametersFlightMap = new FlightMap();
    creationParametersFlightMap.put(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    creationParametersFlightMap.makeImmutable();
    when(mockFlightContext.getInputParameters()).thenReturn(creationParametersFlightMap);

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    // throw a 4xx
    var mockResponse = mock(HttpResponse.class);
    when(mockResponse.getStatusCode()).thenReturn(418);
    var notFoundException =
        new ManagementException(
            "error", mockResponse, new ManagementError("unknown error code", "Error"));
    when(mockVmStage12.create(any(Context.class))).thenThrow(notFoundException);

    StepResult stepResult = createAzureVmStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void deleteVm() throws InterruptedException {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    StepResult stepResult = createAzureVmStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockVms)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }

  @Test
  void addSizeAndPriorityStep_regular() {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    var sizeCaptor = ArgumentCaptor.forClass(VirtualMachineSizeTypes.class);
    var priorityCaptor = ArgumentCaptor.forClass(VirtualMachinePriorityTypes.class);

    // Calculate the VM size and priority
    createAzureVmStep.addSizeAndPriorityStep(mockVmStage11, creationParameters);

    // Verify mock interactions
    verify(mockVmStage11).withSize(sizeCaptor.capture());
    assertThat(sizeCaptor.getValue(), equalTo(VirtualMachineSizeTypes.STANDARD_D2S_V3));

    verify(mockVmStage12).withPriority(priorityCaptor.capture());
    assertThat(priorityCaptor.getValue(), equalTo(VirtualMachinePriorityTypes.REGULAR));
  }

  @Test
  void addSizeAndPriorityStep_spot() {
    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters()
            .priority(ApiAzureVmCreationParameters.PriorityEnum.SPOT);

    var createAzureVmStep =
        new CreateAzureVmStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureVm(creationParameters),
            mockResourceDao);

    var sizeCaptor = ArgumentCaptor.forClass(VirtualMachineSizeTypes.class);
    var priorityCaptor = ArgumentCaptor.forClass(VirtualMachinePriorityTypes.class);

    // Calculate the VM size and priority
    createAzureVmStep.addSizeAndPriorityStep(mockVmStage11, creationParameters);

    // Verify mock interactions
    verify(mockVmStage11).withSize(sizeCaptor.capture());
    assertThat(sizeCaptor.getValue(), equalTo(VirtualMachineSizeTypes.STANDARD_D2S_V3));

    verify(mockVmStage12).withPriority(priorityCaptor.capture());
    assertThat(priorityCaptor.getValue(), equalTo(VirtualMachinePriorityTypes.SPOT));
  }
}
