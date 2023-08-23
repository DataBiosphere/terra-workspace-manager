package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Attributes class for serializing {@link ControlledGceInstanceResource} as json. */
public class ControlledGceInstanceAttributes {
  private final String instanceId;
  private final String zone;
  private final String projectId;

  @JsonCreator
  public ControlledGceInstanceAttributes(
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("zone") String zone,
      @JsonProperty("projectId") String projectId) {
    this.instanceId = instanceId;
    this.zone = zone;
    this.projectId = projectId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getZone() {
    return zone;
  }

  public String getProjectId() {
    return projectId;
  }
}
