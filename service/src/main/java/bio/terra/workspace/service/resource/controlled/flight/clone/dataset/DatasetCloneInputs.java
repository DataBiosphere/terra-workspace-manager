package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

public class DatasetCloneInputs {
  private final UUID workspaceUuid;
  private final String projectId;
  private final String datasetName;

  public DatasetCloneInputs(UUID workspaceUuid, String projectId, String datasetName) {
    this.workspaceUuid = workspaceUuid;
    this.projectId = projectId;
    this.datasetName = datasetName;
  }

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  @Override
  public String toString() {
    return "DatasetCloneInputs{"
        + "workspaceUuid="
        + workspaceUuid
        + ", projectId='"
        + projectId
        + '\''
        + ", datasetName='"
        + datasetName
        + '\''
        + '}';
  }

  /**
   * The dataset ID includes the project name.
   *
   * @return DatasetId
   */
  @JsonIgnore
  public String getDatasetId() {
    return String.format("projects/%s/datasets/%s", getProjectId(), getDatasetName());
  }
}
