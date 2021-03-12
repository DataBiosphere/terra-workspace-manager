package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedBigQueryDatasetAttributes {
  private final String projectId;
  private final String datasetName;

  @JsonCreator
  public ReferencedBigQueryDatasetAttributes(
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
