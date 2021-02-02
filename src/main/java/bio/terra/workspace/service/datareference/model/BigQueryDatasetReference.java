package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.generated.model.GoogleBigQueryDatasetUid;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/**
 * A representation of a reference to a BigQuery dataset.
 *
 * <p>The {@Code JsonTypeName} annotation specifies the class name used for serialization (see the
 * {@Code JsonSubTypes} annotation in {@Code ReferenceObject} for corresponding deserialization). By
 * using a constant string instead of the actual class name, changing the name of this class will
 * not break backwards compatibility with existing serialized objects. This string does not need to
 * match the class name - it only matches for clarity.
 */
@AutoValue
@JsonTypeName("BigQueryDatasetReference")
public abstract class BigQueryDatasetReference implements ReferenceObject {

  @JsonCreator
  public static BigQueryDatasetReference create(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName) {
    return new AutoValue_BigQueryDatasetReference(projectId, datasetName);
  }

  @JsonProperty("projectId")
  public abstract String projectId();

  @JsonProperty("datasetName")
  public abstract String datasetName();

  @Override
  public String toJson() {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Error serializing BigQueryReference", e);
    }
  }

  /** Convenience method for translating this to its equivalent API representation. */
  public GoogleBigQueryDatasetUid toApiModel() {
    return new GoogleBigQueryDatasetUid().projectId(projectId()).datasetId(datasetName());
  }
}
