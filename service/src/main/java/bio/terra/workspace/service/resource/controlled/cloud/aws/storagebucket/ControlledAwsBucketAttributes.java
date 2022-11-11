package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsBucketAttributes {

  @JsonCreator
  public ControlledAwsBucketAttributes(
      @JsonProperty("terraBucketName") String terraBucketName,
      @JsonProperty("s3BucketName") String s3BucketName,
      @JsonProperty("prefix") String prefix) {
    this.terraBucketName = terraBucketName;
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
  }

  private final String terraBucketName;
  private final String s3BucketName;
  private final String prefix;

  public String getTerraBucketName() {
    return terraBucketName;
  }

  public String getS3BucketName() {
    return s3BucketName;
  }

  public String getPrefix() {
    return prefix;
  }
}
