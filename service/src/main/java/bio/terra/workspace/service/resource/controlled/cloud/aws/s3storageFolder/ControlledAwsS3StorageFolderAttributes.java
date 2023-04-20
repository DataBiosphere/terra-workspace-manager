package bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsS3StorageFolderAttributes {

  @JsonCreator
  public ControlledAwsS3StorageFolderAttributes(
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