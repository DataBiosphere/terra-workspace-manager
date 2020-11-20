package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.DataRepoSnapshot;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;

/**
 * Representation of a reference to a Data Repo snapshot.
 *
 * <p>A snapshot is uniquely identified by two fields: instanceName (the name of the data repo
 * instance this reference is stored in. The list of allowed names is a configuration property)
 * snapshot (the ID of the snapshot inside Data Repo)
 */
@AutoValue
public abstract class SnapshotReference implements ReferenceObject {

  /**
   * This objectMapper is static to SnapshotReference so changes to other object mappers will not
   * affect serialization of this class.
   */
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @JsonCreator
  public static SnapshotReference create(
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshot") String snapshot) {
    return new AutoValue_SnapshotReference(instanceName, snapshot);
  }

  @JsonProperty("instanceName")
  public abstract String instanceName();

  @JsonProperty("snapshot")
  public abstract String snapshot();

  @Override
  public String toJson() {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error serializing SnapshotReference", e);
    }
  }

  public static ReferenceObject fromJson(String jsonString) {
    try {
      // This must deserialize to a SnapshotReference and not an AutoValue_SnapshotReference, as
      // the AutoValue class has no annotated constructors or factory methods.
      return objectMapper.readValue(jsonString, SnapshotReference.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error deserializing to SnapshotReference " + jsonString, e);
    }
  }

  /** Convenience method for translating this to its equivalent API representation. */
  public DataRepoSnapshot toApiModel() {
    return new DataRepoSnapshot().instanceName(instanceName()).snapshot(snapshot());
  }
}
