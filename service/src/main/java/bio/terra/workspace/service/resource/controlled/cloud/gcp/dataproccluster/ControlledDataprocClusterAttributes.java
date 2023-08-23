package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Attributes class for serializing {@link ControlledDataprocClusterResource} as json. */
public class ControlledDataprocClusterAttributes {
  private final String clusterId;
  private final String region;
  private final String projectId;

  @JsonCreator
  public ControlledDataprocClusterAttributes(
      @JsonProperty("clusterId") String clusterId,
      @JsonProperty("region") String region,
      @JsonProperty("projectId") String projectId) {
    this.clusterId = clusterId;
    this.region = region;
    this.projectId = projectId;
  }

  public String getClusterId() {
    return clusterId;
  }

  public String getRegion() {
    return region;
  }

  public String getProjectId() {
    return projectId;
  }
}
