package bio.terra.workspace.service.workspace.model;

public class CloudContextHolder {
  private AwsCloudContext awsCloudContext;
  private AzureCloudContext azureCloudContext;
  private GcpCloudContext gcpCloudContext;

  public CloudContextHolder() {}

  public AwsCloudContext getAwsCloudContext() {
    return awsCloudContext;
  }

  public AzureCloudContext getAzureCloudContext() {
    return azureCloudContext;
  }

  public GcpCloudContext getGcpCloudContext() {
    return gcpCloudContext;
  }

  public void setAwsCloudContext(AwsCloudContext awsCloudContext) {
    this.awsCloudContext = awsCloudContext;
  }

  public void setAzureCloudContext(AzureCloudContext azureCloudContext) {
    this.azureCloudContext = azureCloudContext;
  }

  public void setGcpCloudContext(GcpCloudContext gcpCloudContext) {
    this.gcpCloudContext = gcpCloudContext;
  }
}
