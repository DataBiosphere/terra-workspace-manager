package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.Subnet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateAzureNetworkInterfaceStepTest extends BaseAzureUnitTest {

  @Mock private NetworkManager networkManager;

  @Mock private AzureConfiguration azureConfiguration;
  @Mock private CrlService crlService;
  @Mock private ControlledAzureVmResource resource;
  @Mock private SamService samService;

  @Mock private ResourceDao resourceDao;

  @Mock private LandingZoneApiDispatch landingZoneApiDispatch;

  @Mock private ControlledAzureNetworkResource controlledAzureNetworkResource;

  @Mock private Networks networks;

  @Mock private AzureCloudContext azureCloudContext;

  private final String STUB_MRG = "RG";

  private final String STUB_SUBNET = "SUBNET";

  private CreateAzureNetworkInterfaceStep networkInterfaceStep;

  @Mock private Network armNetwork;

  @Mock private Subnet armSubnet;

  private final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest().token(Optional.of("some-token"));

  @BeforeEach
  void setUp() {
    networkInterfaceStep =
        new CreateAzureNetworkInterfaceStep(
            azureConfiguration,
            crlService,
            resource,
            resourceDao,
            landingZoneApiDispatch,
            samService);
    when(networkManager.networks()).thenReturn(networks);
    var subnets = new HashMap<String, Subnet>();
    subnets.put(STUB_SUBNET, armSubnet);
    when(armNetwork.subnets()).thenReturn(subnets);
  }

  @Test
  void getExistingNetworkResources_networkIdIsProvided_returnsWorkspaceNetwork() {
    var networkId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();

    setUpNetworkInWorkspaceInteractionChain(networkId, workspaceId);

    NetworkSubnetPair result =
        networkInterfaceStep.getExistingNetworkResources(azureCloudContext, networkManager);

    assertThat(result.network(), equalTo(armNetwork));
    assertThat(result.subnet(), equalTo(armSubnet));
  }

  @Test
  void getExistingNetworkResources_networkIdIsNotProvided_returnsLZNetwork() {
    var networkId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();

    setUpNetworkInLZInteractionChain(networkId, workspaceId);

    NetworkSubnetPair result =
        networkInterfaceStep.getExistingNetworkResources(azureCloudContext, networkManager);

    assertThat(result.network(), equalTo(armNetwork));
    assertThat(result.subnet(), equalTo(armSubnet));
  }

  private void setUpNetworkInWorkspaceInteractionChain(UUID networkId, UUID workspaceId) {
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_MRG);
    when(resource.getNetworkId()).thenReturn(networkId);
    when(resource.getWorkspaceId()).thenReturn(workspaceId);
    when(resourceDao.getResource(workspaceId, networkId))
        .thenReturn(controlledAzureNetworkResource);
    when(controlledAzureNetworkResource.castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK))
        .thenReturn(controlledAzureNetworkResource);
    when(controlledAzureNetworkResource.getNetworkName()).thenReturn(networkId.toString());
    when(controlledAzureNetworkResource.getSubnetName()).thenReturn(STUB_SUBNET);
    when(networks.getByResourceGroup(STUB_MRG, networkId.toString())).thenReturn(armNetwork);
  }

  private void setUpNetworkInLZInteractionChain(UUID networkId, UUID workspaceId) {
    var lzId = UUID.randomUUID();
    var response = new ApiAzureLandingZoneResourcesList();
    var bearerToken = new BearerToken("wsm-token");
    response.addResourcesItem(
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose(SubnetResourcePurpose.WORKSPACE_COMPUTE_SUBNET.toString())
            .deployedResources(
                List.of(
                    new ApiAzureLandingZoneDeployedResource()
                        .resourceName(STUB_SUBNET)
                        .resourceParentId(networkId.toString()))));

    when(resource.getNetworkId()).thenReturn(null);
    when(resource.getWorkspaceId()).thenReturn(workspaceId);
    when(landingZoneApiDispatch.getLandingZoneId(eq(bearerToken), eq(workspaceId)))
        .thenReturn(lzId);
    when(landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            any(), eq(lzId), eq(SubnetResourcePurpose.WORKSPACE_COMPUTE_SUBNET)))
        .thenReturn(response);
    when(networks.getById(networkId.toString())).thenReturn(armNetwork);
    when(samService.getWsmServiceAccountToken()).thenReturn(bearerToken.getToken());
  }
}
