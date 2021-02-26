package bio.terra.workspace.service.workspace.model;

public class GcpCloudContext {
  private final String gcpProjectId;

  public GcpCloudContext(String gcpProjectId) {
    this.gcpProjectId = gcpProjectId;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }
}
