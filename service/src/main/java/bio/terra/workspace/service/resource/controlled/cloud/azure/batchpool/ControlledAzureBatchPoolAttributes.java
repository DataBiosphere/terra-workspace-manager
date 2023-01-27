package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureBatchPoolAttributes {
  private String id;
  private String vmSize;

  @JsonCreator
  public ControlledAzureBatchPoolAttributes(
      @JsonProperty("id") String id, @JsonProperty("vmSize") String vmSize) {
    this.id = id;
    this.vmSize = vmSize;
  }

  public String getId() {
    return id;
  }

  public String getVmSize() {
    return vmSize;
  }
}
