package bio.terra.workspace.common.utils;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
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
  private final BufferService bufferService;
  private final CrlService crlService;
  private final DataRepoService dataRepoService;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final WorkspaceDao workspaceDao;
  private final WorkspaceService workspaceService;

  @Lazy
  @Autowired
  public FlightBeanBag(
      BufferService bufferService,
      CrlService crlService,
      DataRepoService dataRepoService,
      ResourceDao resourceDao,
      SamService samService,
      WorkspaceDao workspaceDao,
      WorkspaceService workspaceService) {
    this.bufferService = bufferService;
    this.crlService = crlService;
    this.dataRepoService = dataRepoService;
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
  }

  public static FlightBeanBag getFromObject(Object object) {
    return (FlightBeanBag) object;
  }

  public BufferService getBufferService() {
    return bufferService;
  }

  public CrlService getCrlService() {
    return crlService;
  }

  public DataRepoService getDataRepoService() {
    return dataRepoService;
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
}
