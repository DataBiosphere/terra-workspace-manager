package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledBigQueryDatasetAttributes {
  private final String projectId;
  private final String datasetName;

  @JsonCreator
  public ControlledBigQueryDatasetAttributes(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName) {
    this.projectId = projectId;
    this.datasetName = datasetName;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }
}
