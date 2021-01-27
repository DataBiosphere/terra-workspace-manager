package bio.terra.workspace.service.datareference.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/** TODO javadoc */
@AutoValue
@JsonTypeName("BigQueryReference")
public abstract class BigQueryReference implements ReferenceObject {

  @JsonCreator
  public static BigQueryReference create(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName) {
    return new AutoValue_BigQueryReference(projectId, datasetName);
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
}
