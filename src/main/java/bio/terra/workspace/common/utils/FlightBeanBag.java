package bio.terra.workspace.common.utils;

import bio.terra.workspace.app.configuration.external.GoogleWorkspaceConfiguration;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final DataReferenceDao dataReferenceDao;
  private final GoogleWorkspaceConfiguration googleWorkspaceConfiguration;
  private final ObjectMapper objectMapper;
  private final SamService samService;
  private final TransactionTemplate transactionTemplate;
  private final WorkspaceDao workspaceDao;

  @Autowired
  public FlightBeanBag(
      BufferService bufferService,
      CrlService crlService,
      DataReferenceDao dataReferenceDao,
      GoogleWorkspaceConfiguration googleWorkspaceConfiguration,
      ObjectMapper objectMapper,
      SamService samService,
      TransactionTemplate transactionTemplate,
      WorkspaceDao workspaceDao) {
    this.bufferService = bufferService;
    this.crlService = crlService;
    this.dataReferenceDao = dataReferenceDao;
    this.googleWorkspaceConfiguration = googleWorkspaceConfiguration;
    this.objectMapper = objectMapper;
    this.samService = samService;
    this.transactionTemplate = transactionTemplate;
    this.workspaceDao = workspaceDao;
  }

  public static @NotNull FlightBeanBag getFromObject(@NotNull Object object) {
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

  public GoogleWorkspaceConfiguration getGoogleWorkspaceConfiguration() {
    return googleWorkspaceConfiguration;
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
}
