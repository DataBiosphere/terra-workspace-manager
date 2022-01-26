package bio.terra.workspace.service.resource.controlled.azure.network;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureNetworkAttributes {
  private final String networkName;
  private final String subnetName;
  private final String addressSpaceCidr;
  private final String subnetAddressCidr;
  private final String region;

  @JsonCreator
  public ControlledAzureNetworkAttributes(
      @JsonProperty("networkName") String networkName,
      @JsonProperty("subnetName") String subnetName,
      @JsonProperty("addressSpaceCidr") String addressSpaceCidr,
      @JsonProperty("subnetAddressCidr") String subnetAddressCidr,
      @JsonProperty("region") String region) {
    this.networkName = networkName;
    this.subnetName = subnetName;
    this.addressSpaceCidr = addressSpaceCidr;
    this.subnetAddressCidr = subnetAddressCidr;
    this.region = region;
  }

  public String getNetworkName() {
    return networkName;
  }

  public String getSubnetName() {
    return subnetName;
  }

  public String getAddressSpaceCidr() {
    return addressSpaceCidr;
  }

  public String getSubnetAddressCidr() {
    return subnetAddressCidr;
  }

  public String getRegion() {
    return region;
  }
}
