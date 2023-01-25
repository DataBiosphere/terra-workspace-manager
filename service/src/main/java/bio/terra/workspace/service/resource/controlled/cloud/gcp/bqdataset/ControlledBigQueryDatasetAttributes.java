package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledBigQueryDatasetAttributes {
  private final String datasetName;
  private final String projectId;
  private final Long defaultTableLifetime;
  private final Long defaultPartitionLifeTime;

  @JsonCreator
  public ControlledBigQueryDatasetAttributes(
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("defaultTableLifetime") Long defaultTableLifetime,
      @JsonProperty("defaultPartitionLifeTime") Long defaultPartitionLifeTime) {

    this.datasetName = datasetName;
    this.projectId = projectId;
    this.defaultTableLifetime = defaultTableLifetime;
    this.defaultPartitionLifeTime = defaultPartitionLifeTime;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getProjectId() {
    return projectId;
  }

  public Long getDefaultTableLifetime() {
    return defaultTableLifetime;
  }

  public Long getDefaultPartitionLifeTime() {
    return defaultPartitionLifeTime;
  }
}
