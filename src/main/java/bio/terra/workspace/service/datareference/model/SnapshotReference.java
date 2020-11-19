package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.DataRepoSnapshot;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Representation of a reference to a Data Repo snapshot.
 *
 * <p>A snapshot is uniquely identified by two fields: instanceName (the name of the data repo
 * instance this reference is stored in. The list of allowed names is a configuration property)
 * snapshot (the ID of the snapshot inside Data Repo)
 */
@AutoValue
public abstract class SnapshotReference implements ReferenceObject {

  public static String SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY = "instanceName";
  public static String SNAPSHOT_REFERENCE_SNAPSHOT_KEY = "snapshot";

  public static SnapshotReference create(String instanceName, String snapshot) {
    return new AutoValue_SnapshotReference(instanceName, snapshot);
  }

  public abstract String instanceName();

  public abstract String snapshot();

  @Override
  public Map<String, String> getProperties() {
    return ImmutableMap.of(
        SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY,
        instanceName(),
        SNAPSHOT_REFERENCE_SNAPSHOT_KEY,
        snapshot());
  }

  /** Convenience method for translating this to its equivalent API representation. */
  public DataRepoSnapshot toApiModel() {
    return new DataRepoSnapshot().instanceName(instanceName()).snapshot(snapshot());
  }
}
