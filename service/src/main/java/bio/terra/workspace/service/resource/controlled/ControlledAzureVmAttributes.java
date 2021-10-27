package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureVmAttributes {
  private final String vmName;
  private final String region;

  private final UUID ipId;
  private final UUID networkId;
  private final UUID diskId;

  @JsonCreator
  public ControlledAzureVmAttributes(
      @JsonProperty("vmName") String vmName,
      @JsonProperty("region") String region,
      @JsonProperty("ipId") UUID ipId,
      @JsonProperty("networkId") UUID networkId,
      @JsonProperty("diskId") UUID diskId) {
    this.vmName = vmName;
    this.region = region;
    this.ipId = ipId;
    this.networkId = networkId;
    this.diskId = diskId;
  }

  public String getVmName() {
    return vmName;
  }

  public String getRegion() {
    return region;
  }

  public UUID getIpId() {
    return ipId;
  }

  public UUID getNetworkId() {
    return networkId;
  }

  public UUID getDiskId() {
    return diskId;
  }
}
