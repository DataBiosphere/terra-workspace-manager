package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsStorageFolderAttributes {

  @JsonCreator
  public ControlledAwsStorageFolderAttributes(
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("prefix") String prefix,
      @JsonProperty("region") String region) {
    this.bucketName = bucketName;
    this.prefix = prefix;
    this.region = region;
  }

  private final String bucketName;
  private final String prefix;
  private final String region;

  public String getBucketName() {
    return bucketName;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getRegion() {
    return region;
  }
}
