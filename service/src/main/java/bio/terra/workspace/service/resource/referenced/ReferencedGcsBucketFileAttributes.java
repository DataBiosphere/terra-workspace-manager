package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGcsBucketFileAttributes {
  private final String bucketName;
  private final String fileName;

  @JsonCreator
  public ReferencedGcsBucketFileAttributes(
      String bucketName,
      String fileName) {
    this.bucketName = bucketName;
    this.fileName = fileName;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getFileName() {
    return fileName;
  }
}
