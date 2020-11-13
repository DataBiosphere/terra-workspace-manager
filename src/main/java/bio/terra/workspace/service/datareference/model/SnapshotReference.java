package bio.terra.workspace.service.datareference.model;

import java.util.List;
import java.util.Map;

public class SnapshotReference implements ReferenceObject {

  public static List<String> SNAPSHOT_REFERENCE_KEYS = {"reference", "snapshot"};

  @Override
  public Map<String, String> getProperties() {
    return null;
  }

  @Override
  public String getProperty(String key) {
    return null;
  }

  @Override
  public List<String> getPropertyKeys() {
    return null;
  }

  @Override
  public void setProperty(String key, String value) {

  }
}
