package bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedDataRepoSnapshotAttributes {
  private final String instanceName;
  private final String snapshotId;

  @JsonCreator
  public ReferencedDataRepoSnapshotAttributes(
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
