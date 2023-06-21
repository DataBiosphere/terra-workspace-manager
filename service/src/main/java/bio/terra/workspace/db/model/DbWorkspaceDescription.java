package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.OffsetDateTime;

/**
 * The DbWorkspaceDescription contains the full set of information about a workspace that WSM holds
 * in its metadata. It is used for retrieving a single workspace or getting the list of all
 * workspaces.
 *
 * <p>I made this a class instead of a record so that it could be filled in incrementally as we
 * process the data. Otherwise, we need more intermediate forms that don't have a lot of value.
 */
public class DbWorkspaceDescription {
  private Workspace workspace;
  private String lastUpdatedByEmail;
  private OffsetDateTime lastUpdatedByDate;
  private AwsCloudContext awsCloudContext;
  private AzureCloudContext azureCloudContext;
  private GcpCloudContext gcpCloudContext;

  public DbWorkspaceDescription(Workspace workspace) {
    this.workspace = workspace;
  }

  public Workspace getWorkspace() {
    return workspace;
  }

  public DbWorkspaceDescription setWorkspace(Workspace workspace) {
    this.workspace = workspace;
    return this;
  }

  public String getLastUpdatedByEmail() {
    return lastUpdatedByEmail;
  }

  public DbWorkspaceDescription setLastUpdatedByEmail(String lastUpdatedByEmail) {
    this.lastUpdatedByEmail = lastUpdatedByEmail;
    return this;
  }

  public OffsetDateTime getLastUpdatedByDate() {
    return lastUpdatedByDate;
  }

  public DbWorkspaceDescription setLastUpdatedByDate(OffsetDateTime lastUpdatedByDate) {
    this.lastUpdatedByDate = lastUpdatedByDate;
    return this;
  }

  public AwsCloudContext getAwsCloudContext() {
    return awsCloudContext;
  }

  public DbWorkspaceDescription setAwsCloudContext(AwsCloudContext awsCloudContext) {
    this.awsCloudContext = awsCloudContext;
    return this;
  }

  public AzureCloudContext getAzureCloudContext() {
    return azureCloudContext;
  }

  public DbWorkspaceDescription setAzureCloudContext(AzureCloudContext azureCloudContext) {
    this.azureCloudContext = azureCloudContext;
    return this;
  }

  public GcpCloudContext getGcpCloudContext() {
    return gcpCloudContext;
  }

  public DbWorkspaceDescription setGcpCloudContext(GcpCloudContext gcpCloudContext) {
    this.gcpCloudContext = gcpCloudContext;
    return this;
  }

  // Generic setter for any of the cloud contexts
  public DbWorkspaceDescription setCloudContext(CloudContext cloudContext) {
    switch (cloudContext.getCloudPlatform()) {
      case AWS -> awsCloudContext = cloudContext.castByEnum(CloudPlatform.AWS);
      case AZURE -> azureCloudContext = cloudContext.castByEnum(CloudPlatform.AZURE);
      case GCP -> gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);
    }
    return this;
  }
}
