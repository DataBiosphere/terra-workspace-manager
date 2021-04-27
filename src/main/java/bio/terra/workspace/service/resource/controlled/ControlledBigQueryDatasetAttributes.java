package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledBigQueryDatasetAttributes {
  private final String datasetName;

  @JsonCreator
  public ControlledBigQueryDatasetAttributes(@JsonProperty("datasetName") String datasetName) {
    this.datasetName = datasetName;
  }

  public String getDatasetName() {
    return datasetName;
  }
}
