package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsSagemakerNotebookAttributes {
  private final String instanceName;
  private final String instanceType;

  @JsonCreator
  public ControlledAwsSagemakerNotebookAttributes(
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("instanceType") String instanceType) {
    this.instanceName = instanceName;
    this.instanceType = instanceType;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getInstanceType() {
    return instanceType;
  }
}
