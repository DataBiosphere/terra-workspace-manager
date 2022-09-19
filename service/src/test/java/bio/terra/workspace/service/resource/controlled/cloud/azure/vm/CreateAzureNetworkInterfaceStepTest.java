package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.Subnet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("unit")
public class CreateAzureNetworkInterfaceStepTest extends BaseUnitTest {

    @Mock private NetworkManager networkManager;

    @Mock private AzureConfiguration azureConfiguration;
    @Mock private CrlService crlService;
    @Mock private ControlledAzureVmResource resource;

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

    @BeforeEach
    void setUp() {
        networkInterfaceStep =
                new CreateAzureNetworkInterfaceStep(
                        azureConfiguration, crlService, resource, resourceDao, landingZoneApiDispatch);
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
        var lzId = UUID.randomUUID().toString();
        var resources = new ArrayList<ApiAzureLandingZoneDeployedResource>();
        resources.add(
                new ApiAzureLandingZoneDeployedResource()
                        .resourceName(STUB_SUBNET)
                        .resourceParentId(networkId.toString()));

        when(resource.getNetworkId()).thenReturn(null);
        when(landingZoneApiDispatch.getLandingZoneId(azureCloudContext)).thenReturn(lzId);
        when(landingZoneApiDispatch.listSubnetsWithParentVNetByPurpose(
                lzId, SubnetResourcePurpose.WORKSPACE_COMPUTE_SUBNET))
                .thenReturn(resources);
        when(networks.getById(networkId.toString())).thenReturn(armNetwork);
    }
}
