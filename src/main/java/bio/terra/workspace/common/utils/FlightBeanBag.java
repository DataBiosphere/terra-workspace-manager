package bio.terra.workspace.common.utils;

import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ResourceDao resourceDao;
  private final ObjectMapper objectMapper;
  private final SamService samService;
  private final WorkspaceDao workspaceDao;
  private final ControlledResourceDao controlledResourceDao;
  private final DataRepoService dataRepoService;

  @Autowired
  public FlightBeanBag(
      BufferService bufferService,
      CrlService crlService,
      ResourceDao resourceDao,
      ObjectMapper objectMapper,
      SamService samService,
      WorkspaceDao workspaceDao,
      ControlledResourceDao controlledResourceDao,
      DataRepoService dataRepoService) {
    this.bufferService = bufferService;
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.objectMapper = objectMapper;
    this.samService = samService;
    this.workspaceDao = workspaceDao;
    this.controlledResourceDao = controlledResourceDao;
    this.dataRepoService = dataRepoService;
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

  public ResourceDao getResourceDao() {
    return resourceDao;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public SamService getSamService() {
    return samService;
  }

  public WorkspaceDao getWorkspaceDao() {
    return workspaceDao;
  }

  public ControlledResourceDao getControlledResourceDao() {
    return controlledResourceDao;
  }

  public DataRepoService getDataRepoService() {
    return dataRepoService;
  }
}
