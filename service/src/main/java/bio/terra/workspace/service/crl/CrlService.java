package bio.terra.workspace.service.crl;

import bio.terra.cloudres.azure.resourcemanager.common.ResourceManagerRequestData;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.CrlConfiguration;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.exception.CrlInternalException;
import bio.terra.workspace.service.crl.exception.CrlNotInUseException;
import bio.terra.workspace.service.crl.exception.CrlSecurityException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.UserAgentPolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import com.azure.resourcemanager.monitor.MonitorManager;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluentcore.arm.AzureConfigurable;
import com.azure.resourcemanager.storage.StorageManager;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.billing.v1.ProjectBillingInfo;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CrlService {
  /** The client name required by CRL. */
  private static final String CLIENT_NAME = "workspace";

  /** How long to keep the resource before Janitor does the cleanup. */
  private static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofHours(1);

  @Value("${azure.customer.usage-attribute:}")
  private String azureCustomerUsageAttribute;

  private final ClientConfig clientConfig;
  private final CrlConfiguration crlConfig;
  private final AIPlatformNotebooksCow crlNotebooksCow;
  private final DataprocCow crlDataprocCow;
  private final CloudResourceManagerCow crlResourceManagerCow;
  private final CloudBillingClientCow crlBillingClientCow;
  private final CloudComputeCow crlComputeCow;
  private final IamCow crlIamCow;
  private final ServiceUsageCow crlServiceUsageCow;

  @Autowired
  public CrlService(CrlConfiguration crlConfig) {
    this.crlConfig = crlConfig;
    clientConfig = buildClientConfig();
    
    if (crlConfig.getUseCrl()) {
      GoogleCredentials creds = getApplicationCredentials();

      try {
        this.crlNotebooksCow = AIPlatformNotebooksCow.create(clientConfig, creds);
        this.crlDataprocCow = DataprocCow.create(clientConfig, creds);
        this.crlResourceManagerCow = CloudResourceManagerCow.create(clientConfig, creds);
        this.crlBillingClientCow = new CloudBillingClientCow(clientConfig, creds);
        this.crlComputeCow = CloudComputeCow.create(clientConfig, creds);
        this.crlIamCow = IamCow.create(clientConfig, creds);
        this.crlServiceUsageCow = ServiceUsageCow.create(clientConfig, creds);

      } catch (GeneralSecurityException | IOException e) {
        throw new CrlInternalException("Error creating resource manager wrapper", e);
      }
    } else {
      clientConfig = null;
      crlNotebooksCow = null;
      crlDataprocCow = null;
      crlResourceManagerCow = null;
      crlBillingClientCow = null;
      crlComputeCow = null;
      crlIamCow = null;
      crlServiceUsageCow = null;
    }
  }

  /**
   * @return CRL {@link AIPlatformNotebooksCow} which wraps Google AI Platform Notebooks API
   */
  public AIPlatformNotebooksCow getAIPlatformNotebooksCow() {
    assertCrlInUse();
    return crlNotebooksCow;
  }

  /**
   * @return CRL {@link DataprocCow} which wraps Google Dataproc API
   */
  public DataprocCow getDataprocCow() {
    assertCrlInUse();
    return crlDataprocCow;
  }

  /**
   * @return CRL {@link CloudResourceManagerCow} which wraps Google Cloud Resource Manager API
   */
  public CloudResourceManagerCow getCloudResourceManagerCow() {
    assertCrlInUse();
    return crlResourceManagerCow;
  }

  /** Returns the CRL {@link CloudBillingClientCow} which wraps Google Billing API. */
  public CloudBillingClientCow getCloudBillingClientCow() {
    assertCrlInUse();
    return crlBillingClientCow;
  }

  /** Returns the CRL {@link CloudComputeCow} which wraps Google Compute Engine API. */
  public CloudComputeCow getCloudComputeCow() {
    assertCrlInUse();
    return crlComputeCow;
  }

  /** Returns the CRL {@link IamCow} which wraps Google IAM API using WSM SA credentials */
  public IamCow getIamCow() {
    assertCrlInUse();
    return crlIamCow;
  }

  /**
   * @return CRL {@link IamCow} which wraps Google IAM API using user credentials.
   */
  @VisibleForTesting
  public IamCow getIamCow(AuthenticatedUserRequest userRequest) {
    assertCrlInUse();
    try {
      return IamCow.create(clientConfig, GcpUtils.getGoogleCredentialsFromUserRequest(userRequest));
    } catch (GeneralSecurityException | IOException e) {
      throw new CrlInternalException("Error creating IAM API wrapper", e);
    }
  }

  /** Returns the CRL {@link ServiceUsageCow} which wraps Google Cloud ServiceUsage API. */
  public ServiceUsageCow getServiceUsageCow() {
    assertCrlInUse();
    return crlServiceUsageCow;
  }

  /** Returns an Azure {@link ComputeManager} configured for use with CRL. */
  public ComputeManager getComputeManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);

    // We must use FQDN because there are two `Defaults` symbols imported otherwise.
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, ComputeManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  /** Returns an Azure {@link ComputeManager} configured for use with CRL. */
  public RelayManager getRelayManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    RelayManager.Configurable relayManagerConfigurable =
        configureRelayManager(
            bio.terra.cloudres.azure.resourcemanager.relay.Defaults.crlConfigure(
                clientConfig, RelayManager.configure()));
    return relayManagerConfigurable.authenticate(azureCreds, azureProfile);
  }

  /** Returns an Azure {@link StorageManager} configured for use with CRL. */
  public StorageManager getStorageManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, StorageManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  public BatchManager getBatchManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);

    BatchManager.Configurable batchManagerConfigurable =
        configureBatchManager(
            bio.terra.cloudres.azure.resourcemanager.batch.Defaults.crlConfigure(
                clientConfig, BatchManager.configure()));
    return batchManagerConfigurable.authenticate(azureCreds, azureProfile);
  }

  /** Returns an Azure {@link ResourceManager} configured for use with CRL. */
  public ResourceManager getResourceManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, ResourceManager.configure()))
        .authenticate(azureCreds, azureProfile)
        .withSubscription(azureCloudContext.getAzureSubscriptionId());
  }

  /** Returns an Azure {@link MsiManager} configured for use with CRL. */
  public MsiManager getMsiManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, MsiManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  /** Returns an Azure {@link MonitorManager} configured for use with CRL. */
  public MonitorManager getMonitorManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, MonitorManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  public ContainerServiceManager getContainerServiceManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configureAzureResourceManager(
            bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, ContainerServiceManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  public PostgreSqlManager getPostgreSqlManager(
      AzureCloudContext azureCloudContext, AzureConfiguration azureConfig) {
    assertCrlInUse();
    final var azureCreds = getManagedAppCredentials(azureConfig);
    final var azureProfile = getAzureProfile(azureCloudContext);
    return configurePostgreSqlManager(
            bio.terra.cloudres.azure.resourcemanager.postgresflex.Defaults.crlConfigure(
                clientConfig, PostgreSqlManager.configure()))
        .authenticate(azureCreds, azureProfile);
  }

  /**
   * @return CRL {@link BigQueryCow} which wraps Google BigQuery API
   */
  public BigQueryCow createBigQueryCow(AuthenticatedUserRequest userRequest) {
    assertCrlInUse();
    try {
      return BigQueryCow.create(
          clientConfig, GcpUtils.getGoogleCredentialsFromUserRequest(userRequest));
    } catch (IOException | GeneralSecurityException e) {
      throw new CrlInternalException("Error creating BigQuery API wrapper", e);
    }
  }

  /**
   * Create a vanilla Bigquery client object, for testing things that aren't in CRL yet.
   * TODO(jaycarlton): PF-942 implement needed endpoints in CRL and use them here
   *
   * @return Bigquery
   */
  public Bigquery createWsmSaNakedBigQueryClient() {
    assertCrlInUse();
    try {
      return new Bigquery.Builder(
              Defaults.httpTransport(),
              Defaults.jsonFactory(),
              new HttpCredentialsAdapter(
                  GoogleCredentials.getApplicationDefault().createScoped(BigqueryScopes.all())))
          .setApplicationName(clientConfig.getClientName())
          .build();
    } catch (IOException | GeneralSecurityException e) {
      throw new CrlInternalException("Error creating naked BigQuery client.");
    }
  }

  /**
   * @return CRL {@link BigQueryCow} which wraps Google BigQuery API using the WSM service account's
   *     credentials.
   */
  public BigQueryCow createWsmSaBigQueryCow() {
    assertCrlInUse();
    try {
      return BigQueryCow.create(clientConfig, getApplicationCredentials());
    } catch (IOException | GeneralSecurityException e) {
      throw new CrlInternalException("Error creating BigQuery API wrapper", e);
    }
  }

  /**
   * Wrap the BigQuery read access check in its own method. That allows unit tests to mock this
   * service and generate an answer without actually touching BigQuery.
   *
   * <p>The BigQuery API does not expose a "testIamPermissions" endpoint, so we check that the user
   * can list the tables of a dataset as a proxy for read access instead.
   *
   * @param projectId Google project id where the dataset is
   * @param datasetName name of the dataset
   * @param userRequest auth info
   * @return true if the user has permission to list the tables of the given dataset
   */
  public boolean canReadBigQueryDataset(
      String projectId, String datasetName, AuthenticatedUserRequest userRequest) {
    try {
      createBigQueryCow(userRequest).tables().list(projectId, datasetName).execute();
      return true;
    } catch (GoogleJsonResponseException ex) {
      if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND
          || ex.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
        return false;
      }
      throw new InvalidReferenceException("Error while trying to access BigQuery dataset", ex);
    } catch (IOException ex) {
      throw new InvalidReferenceException("Error while trying to access BigQuery dataset", ex);
    }
  }

  /**
   * Wrap the BigQuery read access check in its own method. That allows unit tests to mock this
   * service and generate an answer without actually touching BigQuery.
   *
   * @param projectId Google project id where the dataset is
   * @param datasetName name of the dataset
   * @param dataTableName name of the datatable
   * @param userRequest auth info
   * @return true if the user has permission to the specified table of the given dataset
   */
  public boolean canReadBigQueryDataTable(
      String projectId,
      String datasetName,
      String dataTableName,
      AuthenticatedUserRequest userRequest) {
    try {
      createBigQueryCow(userRequest).tables().get(projectId, datasetName, dataTableName).execute();
      return true;
    } catch (GoogleJsonResponseException ex) {
      if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND
          || ex.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
        return false;
      }
      throw new InvalidReferenceException("Error while trying to access BigQuery dataset", ex);
    } catch (IOException ex) {
      throw new InvalidReferenceException("Error while trying to access BigQuery dataset", ex);
    }
  }

  /**
   * Wrap the BigQuery dataset fetch in its own method. This allows unit tests to mock this service
   * and generate an answer without actually touching BigQuery.
   *
   * @param bigQueryCow BigQuery client object, wrapped by CRL
   * @param projectId Google project id where the dataset is
   * @param datasetName name of the dataset
   * @return the fetched Dataset object
   */
  public static Dataset getBigQueryDataset(
      BigQueryCow bigQueryCow, String projectId, String datasetName) throws IOException {
    return bigQueryCow.datasets().get(projectId, datasetName).execute();
  }

  /**
   * Wrap the BigQuery dataset update in its own method. This allows unit tests to mock this service
   * and generate an answer without actually touching BigQuery.
   *
   * @param bigQueryCow BigQuery client object, wrapped by CRL
   * @param projectId Google project id where the dataset is
   * @param datasetName name of the dataset
   * @param dataset the Dataset object with its properties updated to the new values
   */
  public void updateBigQueryDataset(
      BigQueryCow bigQueryCow, String projectId, String datasetName, Dataset dataset)
      throws IOException {
    try {
      bigQueryCow.datasets().update(projectId, datasetName, dataset).execute();
    } catch (GoogleJsonResponseException gjEx) {
      if (gjEx.getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
        throw new BadRequestException(
            "Error updating BigQuery dataset " + projectId + ", " + datasetName, gjEx);
      }
      throw gjEx;
    }
  }

  /**
   * This creates a storage COW that will operate with WSM credentials optionally in a specific
   * project.
   *
   * @param projectId optional GCP project
   * @return CRL {@link StorageCow} which wraps Google Cloud Storage API
   */
  public StorageCow createStorageCow(@Nullable String projectId) {
    return createStorageCowWorker(projectId, null);
  }

  /**
   * Return a Storage client object from GCS's auto-generated client library, for functionality not
   * present in GCS's preferred client library. Whenever possible, use {@code createStorageCow}
   * instead of this method.
   */
  public Storage createWsmSaNakedStorageClient() {
    try {
      return new Storage.Builder(
              Defaults.httpTransport(),
              Defaults.jsonFactory(),
              new HttpCredentialsAdapter(
                  GoogleCredentials.getApplicationDefault().createScoped(StorageScopes.all())))
          .setApplicationName(clientConfig.getClientName())
          .build();
    } catch (IOException | GeneralSecurityException e) {
      throw new CrlInternalException("Error creating naked Storage client.");
    }
  }

  /**
   * This creates a storage COW that will operate with the user's credentials.
   *
   * @param projectId optional GCP project
   * @param userRequest user auth
   * @return CRL {@link StorageCow} which wraps Google Cloud Storage API in the given project using
   *     provided user credentials.
   */
  public StorageCow createStorageCow(
      @Nullable String projectId, AuthenticatedUserRequest userRequest) {
    return createStorageCowWorker(projectId, userRequest);
  }

  private StorageCow createStorageCowWorker(
      @Nullable String projectId, @Nullable AuthenticatedUserRequest userRequest) {
    assertCrlInUse();

    StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
    if (userRequest != null) {
      optionsBuilder.setCredentials(GcpUtils.getGoogleCredentialsFromUserRequest(userRequest));
    }
    if (!StringUtils.isEmpty(projectId)) {
      optionsBuilder.setProjectId(projectId);
    }
    return new StorageCow(clientConfig, optionsBuilder.build());
  }

  /**
   * Wrap the GcsBucket read access check in its own method. That allows unit tests to mock this
   * service and generate an answer without actually touching CRL.
   *
   * <p>This checks whether a user has either "storage.objects.get" or "storage.objects.list" on a
   * GCP bucket. Either of these permissions allow a user to read the contents of a bucket.
   *
   * @param bucketName bucket of interest
   * @param userRequest auth info
   * @return true if the user has permission to read the contents of the provided bucket
   */
  public boolean canReadGcsBucket(String bucketName, AuthenticatedUserRequest userRequest) {
    // Note that some roles grant "get" permissions but not "list", and vice-versa. Either can be
    // used to read a bucket's contents, so here we only check that the user has at least one.
    final List<String> readPermissions =
        ImmutableList.of("storage.objects.get", "storage.objects.list");
    try {
      StorageCow storage = createStorageCow(null, userRequest);
      List<Boolean> hasPermissionsList = storage.testIamPermissions(bucketName, readPermissions);
      return hasPermissionsList.contains(true);
    } catch (StorageException e) {
      throw new InvalidReferenceException(
          String.format("Error while trying to access GCS bucket %s", bucketName), e);
    }
  }

  public boolean canReadGcsObject(
      String bucketName, String objectName, AuthenticatedUserRequest userRequest) {
    try {
      StorageCow storage = createStorageCow(null, userRequest);
      // If successfully get the blob, the user have at least READER access.
      storage.get(BlobId.of(bucketName, objectName));
      return true;
    } catch (StorageException e) {
      if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
        return false;
      }
      throw new InvalidReferenceException(
          String.format(
              "Error while trying to access GCS blob %s in bucket %s", objectName, bucketName),
          e);
    }
  }

  private ServiceAccountCredentials getJanitorCredentials(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(new FileInputStream(serviceAccountPath));
    } catch (Exception e) {
      throw new CrlSecurityException(
          "Unable to load GoogleCredentials from configuration: " + serviceAccountPath, e);
    }
  }

  public GoogleCredentials getApplicationCredentials() {
    try {
      return GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      throw new CrlSecurityException("Failed to get credentials", e);
    }
  }

  private ClientConfig buildClientConfig() {
    var builder = ClientConfig.Builder.newBuilder().setClient(CLIENT_NAME);
    if (crlConfig.useJanitor()) {
      builder.setCleanupConfig(
          CleanupConfig.builder()
              .setCleanupId(CLIENT_NAME + "-test")
              .setTimeToLive(TEST_RESOURCE_TIME_TO_LIVE)
              .setJanitorProjectId(crlConfig.getJanitorTrackResourceProjectId())
              .setJanitorTopicName(crlConfig.getJanitorTrackResourceTopicId())
              .setCredentials(getJanitorCredentials(crlConfig.getJanitorClientCredentialFilePath()))
              .build());
    }
    return builder.build();
  }

  //  Azure Support

  private TokenCredential getManagedAppCredentials(AzureConfiguration azureConfig) {
    return new ClientSecretCredentialBuilder()
        .clientId(azureConfig.getManagedAppClientId())
        .clientSecret(azureConfig.getManagedAppClientSecret())
        .tenantId(azureConfig.getManagedAppTenantId())
        .build();
  }

  private AzureProfile getAzureProfile(AzureCloudContext azureCloudContext) {
    return new AzureProfile(
        azureCloudContext.getAzureTenantId(),
        azureCloudContext.getAzureSubscriptionId(),
        AzureEnvironment.AZURE);
  }

  @VisibleForTesting
  public ClientConfig getClientConfig() {
    assertCrlInUse();
    return clientConfig;
  }

  public void recordAzureCleanup(ResourceManagerRequestData requestData) {
    bio.terra.cloudres.azure.resourcemanager.common.Defaults.recordCleanup(
        requestData, clientConfig);
  }

  private void assertCrlInUse() {
    if (!crlConfig.getUseCrl()) {
      throw new CrlNotInUseException("Attempt to use CRL when it is set not to be used");
    }
  }

  /**
   * Set the billing account on a GCP project. The main purpose of this method is to allow mocking
   * the setting for unit tests.
   *
   * @param projectId project id string
   * @param billingAccountId billing account id
   */
  public void updateGcpProjectBilling(String projectId, String billingAccountId) {
    ProjectBillingInfo setBilling =
        ProjectBillingInfo.newBuilder()
            .setBillingAccountName("billingAccounts/" + billingAccountId)
            .build();

    getCloudBillingClientCow().updateProjectBillingInfo("projects/" + projectId, setBilling);
  }

  private <T extends AzureConfigurable<T>> T configureAzureResourceManager(T configurable) {
    if (StringUtils.isNotEmpty(azureCustomerUsageAttribute)) {
      configurable.withPolicy(new UserAgentPolicy(azureCustomerUsageAttribute));
    }
    return configurable;
  }

  private RelayManager.Configurable configureRelayManager(RelayManager.Configurable configurable) {
    if (StringUtils.isNotEmpty(azureCustomerUsageAttribute)) {
      configurable.withPolicy(new UserAgentPolicy(azureCustomerUsageAttribute));
    }
    return configurable;
  }

  private BatchManager.Configurable configureBatchManager(BatchManager.Configurable configurable) {
    if (StringUtils.isNotEmpty(azureCustomerUsageAttribute)) {
      configurable.withPolicy(new UserAgentPolicy(azureCustomerUsageAttribute));
    }
    return configurable;
  }

  private PostgreSqlManager.Configurable configurePostgreSqlManager(
      PostgreSqlManager.Configurable configurable) {
    if (StringUtils.isNotEmpty(azureCustomerUsageAttribute)) {
      configurable.withPolicy(new UserAgentPolicy(azureCustomerUsageAttribute));
    }
    return configurable;
  }
}
