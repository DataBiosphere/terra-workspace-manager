package bio.terra.workspace.service.crl;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.bigquery.DatasetCow;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.workspace.app.configuration.external.CrlConfiguration;
import bio.terra.workspace.service.crl.exception.CrlInternalException;
import bio.terra.workspace.service.crl.exception.CrlNotInUseException;
import bio.terra.workspace.service.crl.exception.CrlSecurityException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
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
  private final CloudResourceManagerCow crlResourceManagerCow;
  private final CloudBillingClientCow crlBillingClientCow;
  private final IamCow crlIamCow;
  private final ServiceUsageCow crlServiceUsageCow;

  @Autowired
  public CrlService(CrlConfiguration crlConfig) {
    this.crlConfig = crlConfig;

    if (crlConfig.getUseCrl()) {
      GoogleCredentials creds = getApplicationCredentials();
      clientConfig = buildClientConfig();
      try {
        this.crlResourceManagerCow = CloudResourceManagerCow.create(clientConfig, creds);
        this.crlBillingClientCow = new CloudBillingClientCow(clientConfig, creds);
        this.crlIamCow = IamCow.create(clientConfig, creds);
        this.crlServiceUsageCow = ServiceUsageCow.create(clientConfig, creds);

      } catch (GeneralSecurityException | IOException e) {
        throw new CrlInternalException("Error creating resource manager wrapper", e);
      }
    } else {
      clientConfig = null;
      crlResourceManagerCow = null;
      crlBillingClientCow = null;
      crlIamCow = null;
      crlServiceUsageCow = null;
    }
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
  public BigQueryCow createBigQueryCow(String projectId, AuthenticatedUserRequest userReq) {
    assertCrlInUse();
    return new BigQueryCow(
        clientConfig,
        BigQueryOptions.newBuilder()
            .setCredentials(googleCredentialsFromUserReq(userReq))
            .setProjectId(projectId)
            .build());
  }

  /**
   * Wrap the BigQuery existence check in its own method. That allows unit tests to mock this
   * service and generate an answer without actually touching BigQuery.
   *
   * @param projectId Google project id where the dataset is
   * @param datasetName name of the dataset
   * @param userRequest auth info
   * @return true if the dataset exists
   */
  public boolean bigQueryDatasetExists(
      String projectId, String datasetName, AuthenticatedUserRequest userRequest) {
    try {
      DatasetId datasetId = DatasetId.of(projectId, datasetName);
      // BigQueryCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      DatasetCow dataset = createBigQueryCow(projectId, userRequest).getDataset(datasetId);
      return (dataset != null);
    } catch (BigQueryException e) {
      throw new InvalidReferenceException("Error while trying to access BigQuery dataset", e);
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
   * Wrap the GcsBucket existence check in its own method. That allows unit tests to mock this
   * service and generate an answer without actually touching CRL
   *
   * @param bucketName bucket of interest
   * @param userRequest auth info
   * @return true if the bucket exists
   */
  public boolean gcsBucketExists(String bucketName, AuthenticatedUserRequest userRequest) {
    try {
      BucketCow bucket = createStorageCow(null, userRequest).get(bucketName);
      return (bucket != null);
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

  private void assertCrlInUse() {
    if (!crlConfig.getUseCrl()) {
      throw new CrlNotInUseException("Attempt to use CRL when it is set not to be used");
    }
  }
}
