package bio.terra.workspace.service.workspace.model;

public class CloudContextHolder {
  private GcpCloudContext gcpCloudContext;
  private AzureCloudContext azureCloudContext;
  private AwsCloudContext awsCloudContext;

  public CloudContextHolder() {}

  public GcpCloudContext getGcpCloudContext() {
    return gcpCloudContext;
  }

  public AzureCloudContext getAzureCloudContext() {
    return azureCloudContext;
  }

  public AwsCloudContext getAwsCloudContext() {
    return awsCloudContext;
  }

  public void setGcpCloudContext(GcpCloudContext gcpCloudContext) {
    this.gcpCloudContext = gcpCloudContext;
  }

  public void setAzureCloudContext(AzureCloudContext azureCloudContext) {
    this.azureCloudContext = azureCloudContext;
  }

  public void setAwsCloudContext(AwsCloudContext awsCloudContext) {
    this.awsCloudContext = awsCloudContext;
  }
}
