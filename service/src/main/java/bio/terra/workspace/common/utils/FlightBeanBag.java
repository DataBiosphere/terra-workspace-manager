package bio.terra.workspace.common.utils;

import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.VersionConfiguration;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.grant.GrantService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.LandingZoneBatchAccountFinder;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.BucketCloneRolesService;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The purpose of FlightBeanBag is to provide a clean interface for flights to get access to
 * singleton Spring components. This avoids the use of dynamic bean lookups in flights and casting
 * the lookup result. Instead, flights make calls to accessors in this class. Spring will wire up
 * the underlying methods once at startup avoiding the bean lookup. The objects will be properly
 * types without casting.
 */
@Component
public class FlightBeanBag {
  private final ApplicationDao applicationDao;
  private final GcpCloudContextService gcpCloudContextService;
  private final AzureCloudContextService azureCloudContextService;
  private final AzureConfiguration azureConfig;
  private final AwsCloudContextService awsCloudContextService;
  private final BucketCloneRolesService bucketCloneRolesService;
  private final BufferService bufferService;
  private final CliConfiguration cliConfiguration;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final ControlledResourceService controlledResourceService;
  private final CrlService crlService;
  private final DataRepoService dataRepoService;
  private final FeatureConfiguration featureConfiguration;
  private final FolderDao folderDao;
  private final GrantDao grantDao;
  private final GrantService grantService;
  private final PetSaService petSaService;
  private final ReferencedResourceService referencedResourceService;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final SpendProfileService spendProfileService;
  private final Storagetransfer storagetransfer;
  private final TpsApiDispatch tpsApiDispatch;
  private final WorkspaceDao workspaceDao;
  private final WorkspaceService workspaceService;
  private final VersionConfiguration versionConfiguration;
  private final StorageAccountKeyProvider storageAccountKeyProvider;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  private final LandingZoneBatchAccountFinder landingZoneBatchAccountFinder;

  @Lazy
  @Autowired
  public FlightBeanBag(
      ApplicationDao applicationDao,
      GcpCloudContextService gcpCloudContextService,
      AzureCloudContextService azureCloudContextService,
      AzureConfiguration azureConfig,
      AwsCloudContextService awsCloudContextService,
      BucketCloneRolesService bucketCloneRolesService,
      BufferService bufferService,
      CliConfiguration cliConfiguration,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      ControlledResourceService controlledResourceService,
      CrlService crlService,
      DataRepoService dataRepoService,
      FeatureConfiguration featureConfiguration,
      FolderDao folderDao,
      GrantDao grantDao,
      GrantService grantService,
      PetSaService petSaService,
      TpsApiDispatch tpsApiDispatch,
      ReferencedResourceService referencedResourceService,
      ResourceDao resourceDao,
      SamService samService,
      SpendProfileService spendProfileService,
      Storagetransfer storagetransfer,
      WorkspaceDao workspaceDao,
      WorkspaceService workspaceService,
      VersionConfiguration versionConfiguration,
      StorageAccountKeyProvider storageAccountKeyProvider,
      LandingZoneApiDispatch landingZoneApiDispatch,
      WorkspaceActivityLogService workspaceActivityLogService,
      LandingZoneBatchAccountFinder landingZoneBatchAccountFinder) {
    this.applicationDao = applicationDao;
    this.gcpCloudContextService = gcpCloudContextService;
    this.azureCloudContextService = azureCloudContextService;
    this.azureConfig = azureConfig;
    this.awsCloudContextService = awsCloudContextService;
    this.bucketCloneRolesService = bucketCloneRolesService;
    this.bufferService = bufferService;
    this.cliConfiguration = cliConfiguration;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.controlledResourceService = controlledResourceService;
    this.crlService = crlService;
    this.dataRepoService = dataRepoService;
    this.featureConfiguration = featureConfiguration;
    this.folderDao = folderDao;
    this.grantDao = grantDao;
    this.grantService = grantService;
    this.petSaService = petSaService;
    this.referencedResourceService = referencedResourceService;
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
    this.storagetransfer = storagetransfer;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
    this.versionConfiguration = versionConfiguration;
    this.storageAccountKeyProvider = storageAccountKeyProvider;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
    this.landingZoneBatchAccountFinder = landingZoneBatchAccountFinder;
  }

  public static FlightBeanBag getFromObject(Object object) {
    return (FlightBeanBag) object;
  }

  public ApplicationDao getApplicationDao() {
    return applicationDao;
  }

  public GcpCloudContextService getGcpCloudContextService() {
    return gcpCloudContextService;
  }

  public AzureCloudContextService getAzureCloudContextService() {
    return azureCloudContextService;
  }

  public AzureConfiguration getAzureConfig() {
    return azureConfig;
  }

  public AwsCloudContextService getAwsCloudContextService() {
    return awsCloudContextService;
  }

  public BucketCloneRolesService getBucketCloneRolesService() {
    return bucketCloneRolesService;
  }

  public BufferService getBufferService() {
    return bufferService;
  }

  public ControlledResourceMetadataManager getControlledResourceMetadataManager() {
    return controlledResourceMetadataManager;
  }

  public ControlledResourceService getControlledResourceService() {
    return controlledResourceService;
  }

  public CrlService getCrlService() {
    return crlService;
  }

  public DataRepoService getDataRepoService() {
    return dataRepoService;
  }

  public FeatureConfiguration getFeatureConfiguration() {
    return featureConfiguration;
  }

  public GrantDao getGrantDao() {
    return grantDao;
  }

  public GrantService getGrantService() {
    return grantService;
  }

  public PetSaService getPetSaService() {
    return petSaService;
  }

  public ReferencedResourceService getReferencedResourceService() {
    return referencedResourceService;
  }

  public ResourceDao getResourceDao() {
    return resourceDao;
  }

  public SamService getSamService() {
    return samService;
  }

  public Storagetransfer getStoragetransfer() {
    return storagetransfer;
  }

  public TpsApiDispatch getTpsApiDispatch() {
    return tpsApiDispatch;
  }

  public WorkspaceDao getWorkspaceDao() {
    return workspaceDao;
  }

  public WorkspaceService getWorkspaceService() {
    return workspaceService;
  }

  public SpendProfileService getSpendProfileService() {
    return spendProfileService;
  }

  public CliConfiguration getCliConfiguration() {
    return cliConfiguration;
  }

  public VersionConfiguration getVersionConfiguration() {
    return versionConfiguration;
  }

  public LandingZoneApiDispatch getLandingZoneApiDispatch() {
    return landingZoneApiDispatch;
  }

  public LandingZoneBatchAccountFinder getLandingZoneBatchAccountFinder() {
    return landingZoneBatchAccountFinder;
  }

  public StorageAccountKeyProvider getStorageAccountKeyProvider() {
    return storageAccountKeyProvider;
  }

  public FolderDao getFolderDao() {
    return folderDao;
  }

  public WorkspaceActivityLogService getWorkspaceActivityLogService() {
    return workspaceActivityLogService;
  }
}
