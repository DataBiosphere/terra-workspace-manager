package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.generated.model.GoogleBucketUid;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/**
 * A representation of a reference to a GCS bucket.
 *
 * <p>The {@Code JsonTypeName} annotation specifies the class name used for serialization (see the
 * {@Code JsonSubTypes} annotation in {@Code ReferenceObject} for corresponding deserialization). By
 * using a constant string instead of the actual class name, changing the name of this class will
 * not break backwards compatibility with existing serialized objects. This string does not need to
 * match the class name - it only matches for clarity.
 */
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
      throw new SerializationException("Error serializing GoogleBucketReference", e);
    }
  }

  /** Convenience method for translating this to its equivalent API representation. */
  public GoogleBucketUid toApiModel() {
    return new GoogleBucketUid().bucketName(bucketName());
  }
}
