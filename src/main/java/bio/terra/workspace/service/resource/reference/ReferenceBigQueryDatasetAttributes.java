package bio.terra.workspace.service.resource.reference;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferenceBigQueryDatasetAttributes {
    private final String projectId;
    private final String datasetName;

    @JsonCreator
    public ReferenceBigQueryDatasetAttributes(
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
