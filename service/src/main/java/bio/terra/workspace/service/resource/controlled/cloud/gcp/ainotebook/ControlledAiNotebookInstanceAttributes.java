package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;

/** Attributes class for serializing {@link ControlledAiNotebookInstanceResource} as json. */
public class ControlledAiNotebookInstanceAttributes {
  private final String instanceId;
  private final String location;
  private final String projectId;
  private final String machineType;
  private final AcceleratorConfig acceleratorConfig;

  @JsonCreator
  public ControlledAiNotebookInstanceAttributes(
      @JsonProperty("instanceId") String instanceName,
      @JsonProperty("location") String location,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("machineType") String machineType,
      @JsonProperty("acceleratorConfig") AcceleratorConfig acceleratorConfig) {
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

  public AcceleratorConfig getAcceleratorConfig() {
    return acceleratorConfig;
  }
}
