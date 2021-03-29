package bio.terra.workspace.service.resource.controlled;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAiNotebookInstanceAttributes {
    private final String instanceName;

    @JsonCreator
    public ControlledAiNotebookInstanceAttributes(@JsonProperty("instanceName") String instanceName) {
        this.instanceName = instanceName;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
