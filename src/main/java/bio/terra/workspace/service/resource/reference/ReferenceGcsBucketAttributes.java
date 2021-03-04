package bio.terra.workspace.service.resource.reference;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferenceGcsBucketAttributes {
  private final String bucketName;

  @JsonCreator
  public ReferenceGcsBucketAttributes(@JsonProperty("bucketName") String bucketName) {
    this.bucketName = bucketName;
  }

  public String getBucketName() {
    return bucketName;
  }
}
