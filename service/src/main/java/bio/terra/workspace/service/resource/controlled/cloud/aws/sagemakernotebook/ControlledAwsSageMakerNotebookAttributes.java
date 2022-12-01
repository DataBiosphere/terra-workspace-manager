package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsSageMakerNotebookAttributes {

  @JsonCreator
  public ControlledAwsSageMakerNotebookAttributes(
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("region") String region,
      @JsonProperty("instanceType") String instanceType) {
    this.instanceId = instanceId;
    this.region = region;
    this.instanceType = instanceType;
  }

  private final String instanceId;
  private final String region;
  private final String instanceType;

  public String getInstanceId() {
    return instanceId;
  }

  public String getRegion() {
    return region;
  }

  public String getInstanceType() {
    return instanceType;
  }
}
