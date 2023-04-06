package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Attributes class for serializing {@link ControlledAiNotebookInstanceResource} as json. */
public class ControlledAiNotebookInstanceAttributes {
  private final String instanceId;
  private final String location;
  private final String projectId;

  @JsonCreator
  public ControlledAiNotebookInstanceAttributes(
      @JsonProperty("instanceId") String instanceName,
      @JsonProperty("location") String location,
      @JsonProperty("projectId") String projectId) {
    this.instanceId = instanceName;
    this.location = location;
    this.projectId = projectId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getLocation() {
    return location;
  }

  public String getProjectId() {
    return projectId;
  }
}
