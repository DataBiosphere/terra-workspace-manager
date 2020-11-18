package bio.terra.workspace.service.datareference.model;

import java.util.Map;
import java.util.Set;

/**
 * An interface representing the subject of a data reference.
 *
 * <p>All referenced objects are described by a set of string key-value pairs. The set of keys
 * available are specific to each referenced object type, as are rules around validation of values.
 */
public interface ReferenceObject {

  Map<String, String> getProperties();

  String getProperty(String key);

  Set<String> getPropertyKeys();

  void setProperty(String key, String value);
}
