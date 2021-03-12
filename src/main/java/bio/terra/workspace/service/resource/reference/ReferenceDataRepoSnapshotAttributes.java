package bio.terra.workspace.service.resource.reference;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferenceDataRepoSnapshotAttributes {
  private final String instanceName;
  private final String snapshotId;

  @JsonCreator
  public ReferenceDataRepoSnapshotAttributes(
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshotId") String snapshotId) {
    this.instanceName = instanceName;
    this.snapshotId = snapshotId;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }
}
