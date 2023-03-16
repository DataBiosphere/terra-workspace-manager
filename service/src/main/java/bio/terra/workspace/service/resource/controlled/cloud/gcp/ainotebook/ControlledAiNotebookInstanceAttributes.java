package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import javax.annotation.Nullable;

/** Attributes class for serializing {@link ControlledAiNotebookInstanceResource} as json. */
public class ControlledAiNotebookInstanceAttributes {
  private final String instanceId;
  private final String location;
  private final String projectId;
  private final String machineType;

  @JsonDeserialize(using = AcceleratorConfigDeserializer.class)
  @Nullable
  private final AcceleratorConfig acceleratorConfig;

  @JsonCreator
  public ControlledAiNotebookInstanceAttributes(
      @JsonProperty("instanceId") String instanceName,
      @JsonProperty("location") String location,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("machineType") String machineType,
      @Nullable @JsonProperty("acceleratorConfig") AcceleratorConfig acceleratorConfig) {
    this.instanceId = instanceName;
    this.location = location;
    this.projectId = projectId;
    this.machineType = machineType;
    this.acceleratorConfig = acceleratorConfig;
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

  public String getMachineType() {
    return machineType;
  }

  @Nullable
  public AcceleratorConfig getAcceleratorConfig() {
    return acceleratorConfig;
  }
}
