package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGcsObjectAttributes {
  private final String bucketName;
  private final String objectName;

  @JsonCreator
  public ReferencedGcsObjectAttributes(
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("objectName") String objectName) {
    this.bucketName = bucketName;
    this.objectName = objectName;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getObjectName() {
    return objectName;
  }
}
