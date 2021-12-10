package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledGcsBucketAttributes {
  private final String bucketName;

  @JsonCreator
  public ControlledGcsBucketAttributes(@JsonProperty("bucketName") String bucketName) {
    this.bucketName = bucketName;
  }

  public String getBucketName() {
    return bucketName;
  }
}
