package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Attributes class for serializing {@link ControlledAiNotebookInstanceResource} as json. */
public class ControlledAiNotebookInstanceAttributes {
  private final String instanceId;
  private final String location;

  @JsonCreator
  public ControlledAiNotebookInstanceAttributes(
      @JsonProperty("instanceId") String instanceName, @JsonProperty("location") String location) {
    this.instanceId = instanceName;
    this.location = location;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getLocation() {
    return location;
  }
}
