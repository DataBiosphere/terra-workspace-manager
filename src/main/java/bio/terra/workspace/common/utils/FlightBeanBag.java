package bio.terra.workspace.common.utils;

import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The purpose of FlightBeanBag is to provide a clean interface for flights to get access to
 * singleton Spring components. This avoids the use of dynamic bean lookups in flights and casting
 * the lookup result. Instead, flights make calls to accessors in this class. Spring will wire up
 * the underlying methods once at startup avoiding the bean lookup. The objects will be properly
 * types without casting.
 *
 * <p>We mark the component @Lazy, otherwise it becomes a source of cyclical dependencies as Spring
 * tries to start up the application.
 */
@Component
public class FlightBeanBag {
  private final BufferService bufferService;
  private final CrlService crlService;
  private final DataReferenceDao dataReferenceDao;
  private final ObjectMapper objectMapper;
  private final SamService samService;
  private final TransactionTemplate transactionTemplate;
  private final WorkspaceDao workspaceDao;
  private final ControlledResourceDao controlledResourceDao;

  @Autowired
  public FlightBeanBag(
      @Lazy BufferService bufferService,
      @Lazy CrlService crlService,
      @Lazy DataReferenceDao dataReferenceDao,
      @Lazy ObjectMapper objectMapper,
      @Lazy SamService samService,
      @Lazy TransactionTemplate transactionTemplate,
      @Lazy WorkspaceDao workspaceDao,
      @Lazy ControlledResourceDao controlledResourceDao) {
    this.bufferService = bufferService;
    this.crlService = crlService;
    this.dataReferenceDao = dataReferenceDao;
    this.objectMapper = objectMapper;
    this.samService = samService;
    this.transactionTemplate = transactionTemplate;
    this.workspaceDao = workspaceDao;
    this.controlledResourceDao = controlledResourceDao;
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

  public DataReferenceDao getDataReferenceDao() {
    return dataReferenceDao;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public SamService getSamService() {
    return samService;
  }

  public TransactionTemplate getTransactionTemplate() {
    return transactionTemplate;
  }

  public WorkspaceDao getWorkspaceDao() {
    return workspaceDao;
  }

  public ControlledResourceDao getControlledResourceDao() {
    return controlledResourceDao;
  }
}
