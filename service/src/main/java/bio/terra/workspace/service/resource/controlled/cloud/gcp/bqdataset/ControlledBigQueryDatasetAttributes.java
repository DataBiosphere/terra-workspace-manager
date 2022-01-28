package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledBigQueryDatasetAttributes {
  private final String datasetName;
  private final String projectId;

  @JsonCreator
  public ControlledBigQueryDatasetAttributes(
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("projectId") String projectId) {
    this.datasetName = datasetName;
    this.projectId = projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getProjectId() {
    return projectId;
  }
}
