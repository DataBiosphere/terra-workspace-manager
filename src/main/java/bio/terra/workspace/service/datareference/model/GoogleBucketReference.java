package bio.terra.workspace.service.datareference.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/** TODO javadoc */
@AutoValue
@JsonTypeName("GoogleBucketReference")
public abstract class GoogleBucketReference implements ReferenceObject {

  @JsonCreator
  public static GoogleBucketReference create(@JsonProperty("bucketName") String bucketName) {
    return new AutoValue_GoogleBucketReference(bucketName);
  }

  @JsonProperty("bucketName")
  public abstract String bucketName();

  @Override
  public String toJson() {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error serializing GoogleBucketReference", e);
    }
  }
}
