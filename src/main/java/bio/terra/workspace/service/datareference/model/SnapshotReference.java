package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.DataRepoSnapshot;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.auto.value.AutoValue;

/**
 * Representation of a reference to a Data Repo snapshot.
 *
 * <p>A snapshot is uniquely identified by two fields: instanceName (the name of the data repo
 * instance this reference is stored in. The list of allowed names is a configuration property)
 * snapshot (the ID of the snapshot inside Data Repo)
 *
 * <p>The {@Code JsonTypeName} annotation specifies the class name used for serialization (see the
 * {@Code JsonSubTypes} annotation in {@Code ReferenceObject} for corresponding deserialization). By
 * using a constant string instead of the actual class name, changing the name of this class will
 * not break backwards compatibility with existing serialized objects. This string does not need to
 * match the class name - it only matches for clarity.
 */
@AutoValue
@JsonTypeName("SnapshotReference")
public abstract class SnapshotReference implements ReferenceObject {

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

  /** Convenience method for translating this to its equivalent API representation. */
  public DataRepoSnapshot toApiModel() {
    return new DataRepoSnapshot().instanceName(instanceName()).snapshot(snapshot());
  }
}
