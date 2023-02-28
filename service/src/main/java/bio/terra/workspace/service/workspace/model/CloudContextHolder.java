package bio.terra.workspace.service.workspace.model;

public class CloudContextHolder {
  private GcpCloudContext gcpCloudContext;
  private AzureCloudContext azureCloudContext;

  public CloudContextHolder() {}

  public GcpCloudContext getGcpCloudContext() {
    return gcpCloudContext;
  }

  public AzureCloudContext getAzureCloudContext() {
    return azureCloudContext;
  }

  public void setGcpCloudContext(GcpCloudContext gcpCloudContext) {
    this.gcpCloudContext = gcpCloudContext;
  }

  public void setAzureCloudContext(AzureCloudContext azureCloudContext) {
    this.azureCloudContext = azureCloudContext;
  }
}
