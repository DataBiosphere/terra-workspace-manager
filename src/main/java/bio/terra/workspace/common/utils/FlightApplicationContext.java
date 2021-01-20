package bio.terra.workspace.common.utils;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.workspace.app.configuration.external.GoogleWorkspaceConfiguration;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The purpose of FlightApplicationContext is to provide a clean interface for flights to get access
 * to singleton Spring components. This avoids the use of dynamic bean lookups in flights and
 * casting the lookup result. Instead, flights make calls to accessors in this class. Spring will
 * wire up the underlying methods once at startup avoiding the bean lookup. The objects will be
 * properly types without casting.
 */
@Component
public class FlightApplicationContext {
  private final BufferService bufferService;
  private final CloudBillingClientCow billingClient;
  private final CloudResourceManagerCow resourceManager;
  private final DataReferenceDao dataReferenceDao;
  private final GoogleWorkspaceConfiguration googleWorkspaceConfiguration;
  private final ObjectMapper objectMapper;
  private final SamService samService;
  private final ServiceUsageCow serviceUsage;
  private final TransactionTemplate transactionTemplate;
  private final WorkspaceDao workspaceDao;

  @Autowired
  public FlightApplicationContext(
      BufferService bufferService,
      CloudBillingClientCow billingClient,
      CloudResourceManagerCow resourceManager,
      DataReferenceDao dataReferenceDao,
      GoogleWorkspaceConfiguration googleWorkspaceConfiguration,
      ObjectMapper objectMapper,
      SamService samService,
      ServiceUsageCow serviceUsage,
      TransactionTemplate transactionTemplate,
      WorkspaceDao workspaceDao) {
    this.bufferService = bufferService;
    this.billingClient = billingClient;
    this.dataReferenceDao = dataReferenceDao;
    this.googleWorkspaceConfiguration = googleWorkspaceConfiguration;
    this.objectMapper = objectMapper;
    this.resourceManager = resourceManager;
    this.samService = samService;
    this.serviceUsage = serviceUsage;
    this.transactionTemplate = transactionTemplate;
    this.workspaceDao = workspaceDao;
  }

  public static FlightApplicationContext getFromObject(Object object) {
    return (FlightApplicationContext) object;
  }

  public BufferService getBufferService() {
    return bufferService;
  }

  public CloudBillingClientCow getBillingClient() {
    return billingClient;
  }

  public CloudResourceManagerCow getResourceManager() {
    return resourceManager;
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

  public ServiceUsageCow getServiceUsage() {
    return serviceUsage;
  }

  public TransactionTemplate getTransactionTemplate() {
    return transactionTemplate;
  }

  public WorkspaceDao getWorkspaceDao() {
    return workspaceDao;
  }
}
