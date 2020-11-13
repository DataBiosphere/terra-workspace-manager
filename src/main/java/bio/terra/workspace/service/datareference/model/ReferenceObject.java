package bio.terra.workspace.service.datareference.model;

import java.util.List;
import java.util.Map;

/** An interface representing the subject of a data reference.
 *
 * All referenced objects are described by a set of string key-value pairs. The set of keys available are specific to each referenced object type, as are rules around validation of values.
 */
public interface ReferenceObject {

  Map<String, String> getProperties();

  String getProperty(String key);

  List<String> getPropertyKeys();

  void setProperty(String key, String value);
}
