package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.GcpContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GcpCloudContext {
  private final String gcpProjectId;

  @JsonCreator
  public GcpCloudContext(@JsonProperty String gcpProjectId) {
    this.gcpProjectId = gcpProjectId;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public GcpContext toApi() {
    return new GcpContext().projectId(getGcpProjectId());
  }
}
