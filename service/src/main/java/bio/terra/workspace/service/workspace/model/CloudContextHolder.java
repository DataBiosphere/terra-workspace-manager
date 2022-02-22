package bio.terra.workspace.service.workspace.model;

public class CloudContextHolder {
  private AzureCloudContext azureCloudContext;
  private GcpCloudContext gcpCloudContext;

  public CloudContextHolder() {}

  public AzureCloudContext getAzureCloudContext() {
    return azureCloudContext;
  }

  public GcpCloudContext getGcpCloudContext() {
    return gcpCloudContext;
  }

  public void setAzureCloudContext(AzureCloudContext azureCloudContext) {
    this.azureCloudContext = azureCloudContext;
  }

  public void setGcpCloudContext(GcpCloudContext gcpCloudContext) {
    this.gcpCloudContext = gcpCloudContext;
  }
}
