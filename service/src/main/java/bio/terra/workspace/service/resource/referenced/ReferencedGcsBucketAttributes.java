package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGcsBucketAttributes {
  private final String bucketName;

  @JsonCreator
  public ReferencedGcsBucketAttributes(@JsonProperty("bucketName") String bucketName) {
    this.bucketName = bucketName;
  }

  public String getBucketName() {
    return bucketName;
  }
}
