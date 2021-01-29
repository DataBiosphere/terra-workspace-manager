package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.BigQueryDataset;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/** TODO javadoc */
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
      throw new RuntimeException("Error serializing BigQueryReference", e);
    }
  }

  /** Convenience method for translating this to its equivalent API representation. */
  public BigQueryDataset toApiModel() {
    return new BigQueryDataset().projectId(projectId()).datasetName(datasetName());
  }
}
