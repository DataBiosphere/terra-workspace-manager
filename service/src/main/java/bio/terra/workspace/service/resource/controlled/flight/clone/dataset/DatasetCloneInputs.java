package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

public class DatasetCloneInputs {
  private final UUID workspaceId;
  private final String projectId;
  private final String datasetName;

  public DatasetCloneInputs(UUID workspaceId, String projectId, String datasetName) {
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.datasetName = datasetName;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
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
        + "workspaceId="
        + workspaceId
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
   * @return
   */
  @JsonIgnore
  public String getDatasetId() {
    return String.format("projects/%s/datasets/%s", getProjectId(), getDatasetName());
  }
}
