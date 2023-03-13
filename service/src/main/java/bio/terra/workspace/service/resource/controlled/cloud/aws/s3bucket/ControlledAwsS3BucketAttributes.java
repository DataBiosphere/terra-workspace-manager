package bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAwsS3BucketAttributes {

  @JsonCreator
  public ControlledAwsS3BucketAttributes(
      @JsonProperty("s3BucketName") String s3BucketName,
      @JsonProperty("prefix") String prefix,
      @JsonProperty("region") String region) {
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
    this.region = region;
  }

  private final String s3BucketName;
  private final String prefix;
  private final String region;

  public String getS3BucketName() {
    return s3BucketName;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getRegion() {
    return region;
  }
}
