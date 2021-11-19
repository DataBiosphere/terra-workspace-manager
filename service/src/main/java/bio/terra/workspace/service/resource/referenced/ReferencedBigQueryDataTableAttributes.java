package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedBigQueryDataTableAttributes {
  private final String projectId;
  private final String datasetName;
  private final String dataTableName;

  @JsonCreator
  public ReferencedBigQueryDataTableAttributes(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("dataTableName") String dataTableName) {
    this.projectId = projectId;
    this.datasetName = datasetName;
    this.dataTableName = dataTableName;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getDataTableName() {
    return dataTableName;
  }
}
