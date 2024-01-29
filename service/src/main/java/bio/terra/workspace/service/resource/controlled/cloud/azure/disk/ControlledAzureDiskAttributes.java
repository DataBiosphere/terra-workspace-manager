package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureDiskAttributes {
  private final String diskName;
  private final String region;

  /** size is in GB */
  private final int size;

  @JsonCreator
  public ControlledAzureDiskAttributes(
      @JsonProperty("diskName") String diskName,
      @JsonProperty("region") String region,
      @JsonProperty("size") int size) {
    this.diskName = diskName;
    this.region = region;
    this.size = size;
  }

  public String getDiskName() {
    return diskName;
  }

  public int getSize() {
    return size;
  }

  public String getRegion() {
    return region;
  }
}
