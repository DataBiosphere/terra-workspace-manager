package bio.terra.workspace.service.workspace.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = CloudContextHolderSerializer.class)
@JsonDeserialize(using = CloudContextHolderDeserializer.class)
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
