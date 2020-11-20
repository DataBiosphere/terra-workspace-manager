package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;

/**
 * An interface representing the subject of a data reference.
 *
 * <p>All referenced objects are read-only value objects described by a set of string key-value
 * pairs. The set of keys available are specific to each referenced object type, as are validation
 * rules. ReferenceObject implementations must be fully serializable by the output of getProperties.
 */
public interface ReferenceObject {

  /**
   * Method for serializing a ReferenceObject to a json string.
   *
   * <p>This method is used for Stairway serialization. Database serialization should use the static
   * {@Code DataReferenceDao.serializeReferenceObject} method instead.
   */
  String toJson();

  /**
   * Method for deserializing a ReferenceObject from a json string and its type.
   *
   * <p>This method is used for Stairway deserialization. Database deserialization should use the
   * static {@Code DataReferenceDao.deserializeReferenceObject} method instead.
   *
   * <p>Implementations of this interface are expected to provide a static method for deserializing
   * from json which should be referenced here. This should
   */
  static ReferenceObject fromJson(String jsonString, DataReferenceType type) {
    switch (type) {
      case DATA_REPO_SNAPSHOT:
        return SnapshotReference.fromJson(jsonString);
      default:
        throw new InvalidDataReferenceException(
            "Attempting to create unsupported reference type " + type);
    }
  }
}
