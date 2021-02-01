package bio.terra.workspace.service.crl;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.workspace.app.configuration.external.CrlConfiguration;
import bio.terra.workspace.service.crl.exception.CrlInternalException;
import bio.terra.workspace.service.crl.exception.CrlNotInUseException;
import bio.terra.workspace.service.crl.exception.CrlSecurityException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CrlService {
  /** The client name required by CRL. */
  private static final String CLIENT_NAME = "workspace";

  /** How long to keep the resource before Janitor does the cleanup. */
  private static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofHours(1);

  private final CrlConfiguration crlConfig;
  private final CloudResourceManagerCow crlResourceManagerCow;
  private final CloudBillingClientCow crlBillingClientCow;
  private final ServiceUsageCow crlServiceUsageCow;

  @Autowired
  public CrlService(CrlConfiguration crlConfig) {
    this.crlConfig = crlConfig;

    if (crlConfig.getUseCrl()) {
      GoogleCredentials creds = getApplicationCredentials();
      final ClientConfig clientConfig = buildClientConfig();
      try {
        this.crlResourceManagerCow = CloudResourceManagerCow.create(clientConfig, creds);
        this.crlBillingClientCow = new CloudBillingClientCow(clientConfig, creds);
        this.crlServiceUsageCow = ServiceUsageCow.create(clientConfig, creds);

      } catch (GeneralSecurityException | IOException e) {
        throw new CrlInternalException("Error creating resource manager wrapper", e);
      }
    } else {
      crlResourceManagerCow = null;
      crlBillingClientCow = null;
      crlServiceUsageCow = null;
    }
  }

  /**
   * @return CRL {@link CloudResourceManagerCow} which wrappers Google Cloud Resource Manager API
   */
  public CloudResourceManagerCow getCloudResourceManagerCow() {
    assertCrlInUse();
    return crlResourceManagerCow;
  }

  /** Returns the CRL {@link CloudBillingClientCow} which wrappers Google Billing API. */
  public CloudBillingClientCow getCloudBillingClientCow() {
    assertCrlInUse();
    return crlBillingClientCow;
  }

  /** Returns the CRL {@link ServiceUsageCow} which wrappers Google Cloud ServiceUsage API. */
  public ServiceUsageCow getServiceUsageCow() {
    assertCrlInUse();
    return crlServiceUsageCow;
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
