package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsS3StorageFolderAttributes {
  private final String bucketName;
  private final String prefix;

  @JsonCreator
  public ControlledAwsS3StorageFolderAttributes(
      @JsonProperty("bucketName") String bucketName, @JsonProperty("prefix") String prefix) {
    this.bucketName = bucketName;
    this.prefix = prefix;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getPrefix() {
    return prefix;
  }
}
