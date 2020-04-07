package bio.terra.workspace.model;

public class DataRepoSnapshot {

  private String snapshotId;
  private String instance;

  public DataRepoSnapshot() {
    super();
  }

  public DataRepoSnapshot(String snapshotId, String instance) {
    this.snapshotId = snapshotId;
    this.instance = instance;
  }

  public String getInstance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(String snapshotId) {
    this.snapshotId = snapshotId;
  }

  @Override
  public String toString() {
    return "{"
        + "\"instance\":"
        + "\""
        + instance
        + "\""
        + ", \"snapshotId\":"
        + "\""
        + snapshotId
        + "\""
        + "}";
  }
}
