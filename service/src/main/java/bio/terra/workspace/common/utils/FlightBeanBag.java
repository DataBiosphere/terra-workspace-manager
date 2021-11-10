package bio.terra.workspace.common.utils;

import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.BucketCloneRolesComponent;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
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
  private final BucketCloneRolesComponent bucketCloneRolesComponent;
  private final BufferService bufferService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final ControlledResourceService controlledResourceService;
  private final CrlService crlService;
  private final DataRepoService dataRepoService;
  private final GcpCloudContextService gcpCloudContextService;
  private final PetSaService petSaService;
  private final ReferencedResourceService referencedResourceService;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final WorkspaceDao workspaceDao;
  private final WorkspaceService workspaceService;
  private final SpendProfileService spendProfileService;

  @Lazy
  @Autowired
  public FlightBeanBag(
      ApplicationDao applicationDao,
      BucketCloneRolesComponent bucketCloneRolesComponent,
      BufferService bufferService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      ControlledResourceService controlledResourceService,
      CrlService crlService,
      DataRepoService dataRepoService,
      GcpCloudContextService gcpCloudContextService,
      PetSaService petSaService,
      ReferencedResourceService referencedResourceService,
      ResourceDao resourceDao,
      SamService samService,
      WorkspaceDao workspaceDao,
      WorkspaceService workspaceService,
      SpendProfileService spendProfileService) {
    this.applicationDao = applicationDao;
    this.bucketCloneRolesComponent = bucketCloneRolesComponent;
    this.bufferService = bufferService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.controlledResourceService = controlledResourceService;
    this.crlService = crlService;
    this.dataRepoService = dataRepoService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.petSaService = petSaService;
    this.referencedResourceService = referencedResourceService;
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
    this.spendProfileService = spendProfileService;
  }

  public static FlightBeanBag getFromObject(Object object) {
    return (FlightBeanBag) object;
  }

  public ApplicationDao getApplicationDao() {
    return applicationDao;
  }

  public BucketCloneRolesComponent getBucketCloneRolesComponent() {
    return bucketCloneRolesComponent;
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

  public GcpCloudContextService getGcpCloudContextService() {
    return gcpCloudContextService;
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

  public WorkspaceDao getWorkspaceDao() {
    return workspaceDao;
  }

  public WorkspaceService getWorkspaceService() {
    return workspaceService;
  }

  public SpendProfileService getSpendProfileService() {
    return spendProfileService;
  }
}
