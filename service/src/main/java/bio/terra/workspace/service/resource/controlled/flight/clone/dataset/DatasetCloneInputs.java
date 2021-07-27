package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import java.util.List;
import java.util.UUID;

public class DatasetCloneInputs {
  private final UUID workspaceId;
  private final String projectId;
  private final String datasetName;
  private final List<String> roleNames;

  public DatasetCloneInputs(
      UUID workspaceId, String projectId, String datasetName, List<String> roleNames) {
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.datasetName = datasetName;
    this.roleNames = roleNames;
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

  public List<String> getRoleNames() {
    return roleNames;
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
        + ", roleNames="
        + roleNames
        + '}';
  }

  /**
   * The dataset ID includes the project name.
   *
   * @return
   */
  public String getDatasetId() {
    return String.format("projects/%s/datasets/%s", getProjectId(), getDatasetName());
  }
}
