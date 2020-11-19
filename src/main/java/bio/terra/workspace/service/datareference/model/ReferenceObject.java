package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import java.util.Map;

/**
 * An interface representing the subject of a data reference.
 *
 * <p>All referenced objects are read-only value objects described by a set of string key-value
 * pairs. The set of keys available are specific to each referenced object type, as are validation
 * rules. ReferenceObject implementations must be fully serializable by the output of getProperties.
 */
public interface ReferenceObject {
  Map<String, String> getProperties();

  /**
   * Convenience method for building a concrete referenceObject from a map and type.
   *
   * <p>This is used for deserializing ReferenceObjects from their properties and type.
   */
  static ReferenceObject toReferenceObject(DataReferenceType type, Map<String, String> properties) {
    switch (type) {
      case DATA_REPO_SNAPSHOT:
        return SnapshotReference.create(
            properties.get(SnapshotReference.SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY),
            properties.get(SnapshotReference.SNAPSHOT_REFERENCE_SNAPSHOT_KEY));
      default:
        throw new InvalidDataReferenceException(
            "Attempting to create unsupported reference type " + type);
    }
  }
}
