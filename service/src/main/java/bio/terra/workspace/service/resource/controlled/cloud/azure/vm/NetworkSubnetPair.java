package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.Subnet;

public record NetworkSubnetPair(Network network, Subnet subnet) {
    static NetworkSubnetPair createNetworkSubnetPair(
            NetworkManager networkManager, String vNetArmId, String subnetName) {
        Network vNet = networkManager.networks().getById(vNetArmId);
        Subnet subnet = vNet.subnets().get(subnetName);

        return new NetworkSubnetPair(vNet, subnet);
    }

    static NetworkSubnetPair createNetworkSubnetPair(
            NetworkManager networkManager, String resourceGroupName, String vNetName, String subnetName) {
        Network vNet = networkManager.networks().getByResourceGroup(resourceGroupName, vNetName);
        Subnet subnet = vNet.subnets().get(subnetName);

        return new NetworkSubnetPair(vNet, subnet);
    }
}