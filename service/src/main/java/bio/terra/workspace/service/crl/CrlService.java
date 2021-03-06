package bio.terra.workspace.service.crl;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.workspace.app.configuration.external.CrlConfiguration;
import bio.terra.workspace.service.crl.exception.CrlInternalException;
import bio.terra.workspace.service.crl.exception.CrlNotInUseException;
import bio.terra.workspace.service.crl.exception.CrlSecurityException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
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
import org.springframework.stereotype.Component;

@Component
public class CrlService {
  /** The client name required by CRL. */
  private static final String CLIENT_NAME = "workspace";

  /** How long to keep the resource before Janitor does the cleanup. */
  private static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofHours(1);

  private final ClientConfig clientConfig;
  private final CrlConfiguration crlConfig;
  private final AIPlatformNotebooksCow crlNotebooksCow;
  private final CloudResourceManagerCow crlResourceManagerCow;
  private final CloudBillingClientCow crlBillingClientCow;
  private final CloudComputeCow crlComputeCow;
  private final IamCow crlIamCow;
  private final ServiceUsageCow crlServiceUsageCow;

  @Autowired
  public CrlService(CrlConfiguration crlConfig) {
    this.crlConfig = crlConfig;

    if (crlConfig.getUseCrl()) {
      GoogleCredentials creds = getApplicationCredentials();
      clientConfig = buildClientConfig();
      try {
        this.crlNotebooksCow = AIPlatformNotebooksCow.create(clientConfig, creds);
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
      crlResourceManagerCow = null;
      crlBillingClientCow = null;
      crlComputeCow = null;
      crlIamCow = null;
      crlServiceUsageCow = null;
    }
  }
  /** @return CRL {@link AIPlatformNotebooksCow} which wraps Google AI Platform Notebooks API */
  public AIPlatformNotebooksCow getAIPlatformNotebooksCow() {
    assertCrlInUse();
    return crlNotebooksCow;
  }

  /** @return CRL {@link CloudResourceManagerCow} which wraps Google Cloud Resource Manager API */
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

  /** Returns the CRL {@link IamCow} which wraps Google IAM API. */
  public IamCow getIamCow() {
    assertCrlInUse();
    return crlIamCow;
  }

  /** Returns the CRL {@link ServiceUsageCow} which wraps Google Cloud ServiceUsage API. */
  public ServiceUsageCow getServiceUsageCow() {
    assertCrlInUse();
    return crlServiceUsageCow;
  }

  /** @return CRL {@link BigQueryCow} which wraps Google BigQuery API */
  public BigQueryCow createBigQueryCow(AuthenticatedUserRequest userReq) {
    assertCrlInUse();
    try {
      return BigQueryCow.create(clientConfig, googleCredentialsFromUserReq(userReq));
    } catch (IOException | GeneralSecurityException e) {
      throw new CrlInternalException("Error creating BigQuery API wrapper", e);
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
   * This creates a storage COW that will operate with the user's credentials.
   *
   * @param projectId optional GCP project
   * @param userReq user auth
   * @return CRL {@link StorageCow} which wraps Google Cloud Storage API in the given project using
   *     provided user credentials.
   */
  public StorageCow createStorageCow(@Nullable String projectId, AuthenticatedUserRequest userReq) {
    return createStorageCowWorker(projectId, userReq);
  }

  private StorageCow createStorageCowWorker(
      @Nullable String projectId, @Nullable AuthenticatedUserRequest userReq) {
    assertCrlInUse();

    StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
    if (userReq != null) {
      optionsBuilder.setCredentials(googleCredentialsFromUserReq(userReq));
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

  private ServiceAccountCredentials getJanitorCredentials(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(new FileInputStream(serviceAccountPath));
    } catch (Exception e) {
      throw new CrlSecurityException(
          "Unable to load GoogleCredentials from configuration: " + serviceAccountPath, e);
    }
  }

  private GoogleCredentials getApplicationCredentials() {
    try {
      return GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      throw new CrlSecurityException("Failed to get credentials", e);
    }
  }

  private GoogleCredentials googleCredentialsFromUserReq(AuthenticatedUserRequest userReq) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(userReq.getRequiredToken(), null);
    return GoogleCredentials.create(accessToken);
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

  @VisibleForTesting
  public ClientConfig getClientConfig() {
    assertCrlInUse();
    return clientConfig;
  }

  private void assertCrlInUse() {
    if (!crlConfig.getUseCrl()) {
      throw new CrlNotInUseException("Attempt to use CRL when it is set not to be used");
    }
  }
}
