package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledGcsBucketAttributes {
  private final String bucketName;
  private final String bucketLocation;

  @JsonCreator
  public ControlledGcsBucketAttributes(
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("bucketLocation") String bucketLocation) {
    this.bucketName = bucketName;
    this.bucketLocation = bucketLocation;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getBucketLocation() {
    return bucketLocation;
  }
}
