package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsSageMakerNotebookAttributes {

  @JsonCreator
  public ControlledAwsSageMakerNotebookAttributes(@JsonProperty("instanceId") String instanceId) {
    this.instanceId = instanceId;
  }

  private final String instanceId;

  public String getInstanceId() {
    return instanceId;
  }
}
