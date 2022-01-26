package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedBigQueryDataTableAttributes {
  private final String projectId;
  private final String datasetId;
  private final String dataTableId;

  @JsonCreator
  public ReferencedBigQueryDataTableAttributes(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetId") String datasetId,
      @JsonProperty("dataTableId") String dataTableId) {
    this.projectId = projectId;
    this.datasetId = datasetId;
    this.dataTableId = dataTableId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public String getDataTableId() {
    return dataTableId;
  }
}
