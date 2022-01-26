package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class ControlledGcsBucketAttributes {
  private final @Nullable String bucketName;

  @JsonCreator
  public ControlledGcsBucketAttributes(@JsonProperty("bucketName") @Nullable String bucketName) {
    this.bucketName = bucketName;
  }

  public @Nullable String getBucketName() {
    return bucketName;
  }
}
