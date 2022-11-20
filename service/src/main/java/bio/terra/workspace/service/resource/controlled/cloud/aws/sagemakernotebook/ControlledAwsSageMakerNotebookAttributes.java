package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsSageMakerNotebookAttributes {

  @JsonCreator
  public ControlledAwsSageMakerNotebookAttributes(
      @JsonProperty("instanceId") String instanceId, @JsonProperty("region") String region) {
    this.instanceId = instanceId;
    this.region = region;
  }

  private final String instanceId;
  private final String region;

  public String getInstanceId() {
    return instanceId;
  }

  public String getRegion() {
    return region;
  }
}
