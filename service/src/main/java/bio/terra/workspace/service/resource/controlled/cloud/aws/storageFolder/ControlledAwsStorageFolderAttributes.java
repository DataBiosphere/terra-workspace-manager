package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsStorageFolderAttributes {

  @JsonCreator
  public ControlledAwsStorageFolderAttributes(
      @JsonProperty("bucketName") String bucketName, @JsonProperty("prefix") String prefix) {
    this.bucketName = bucketName;
    this.prefix = prefix;
  }

  private final String bucketName;
  private final String prefix;

  public String getBucketName() {
    return bucketName;
  }

  public String getPrefix() {
    return prefix;
  }
}
