package bio.terra.workspace.service.resource.reference;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferenceDataRepoSnapshotAttributes {
    private final String instanceName;
    private final String snapshot;

    @JsonCreator
    public ReferenceDataRepoSnapshotAttributes(
            @JsonProperty("instanceName") String instanceName,
            @JsonProperty("snapshot") String snapshot) {
        this.instanceName = instanceName;
        this.snapshot = snapshot;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getSnapshot() {
        return snapshot;
    }
}
