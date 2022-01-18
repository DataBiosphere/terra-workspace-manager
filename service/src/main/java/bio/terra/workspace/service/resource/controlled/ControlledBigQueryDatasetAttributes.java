package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledBigQueryDatasetAttributes {
  private final String datasetName;
  private final String datasetLocation;

  @JsonCreator
  public ControlledBigQueryDatasetAttributes(@JsonProperty("datasetName") String datasetName,
      @JsonProperty("datasetLocation") String datasetLocation) {
    this.datasetName = datasetName;
    this.datasetLocation = datasetLocation;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getDatasetLocation() {
    return datasetLocation;
  }
}
