package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of a reference to a Data Repo snapshot.
 *
 * <p>A snapshot is uniquely identified by two fields: instanceName (the name of the data repo
 * instance this reference is stored in. The list of allowed names is a configuration property)
 * snapshot (the ID of the snapshot inside Data Repo)
 */
public class SnapshotReference implements ReferenceObject {

  public static String SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY = "instanceName";
  public static String SNAPSHOT_REFERENCE_SNAPSHOT_KEY = "snapshot";

  private Map<String, String> propertyMap;

  public SnapshotReference(String instanceName, String snapshot) {
    this.propertyMap = new HashMap<>();
    propertyMap.put(SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY, instanceName);
    propertyMap.put(SNAPSHOT_REFERENCE_SNAPSHOT_KEY, snapshot);
  }

  @Override
  public Map<String, String> getProperties() {
    return propertyMap;
  }

  @Override
  public String getProperty(String key) {
    return propertyMap.get(key);
  }

  @Override
  public List<String> getPropertyKeys() {
    return Arrays.asList(SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY, SNAPSHOT_REFERENCE_SNAPSHOT_KEY);
  }

  // TODO: snapshot validation stuff should go here.
  @Override
  public void setProperty(String key, String value) {
    if (!getPropertyKeys().contains(key)) {
      throw new InvalidDataReferenceException(
          "Invalid field specified for SnapshotReference: " + key);
    }
    propertyMap.put(key, value);
  }
}
