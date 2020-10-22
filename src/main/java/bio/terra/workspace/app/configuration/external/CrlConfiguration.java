package bio.terra.workspace.app.configuration.external;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Configuration to use Terra Cloud Resource Library. */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.crl")
public class CrlConfiguration {
  /** The client name required by CRL. */
  public static final String CLIENT_NAME = "workspace";
  /** How long to keep the resource before Janitor do the cleanup. */
  public static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofHours(1);

  /**
   * Whether we're running RBS in test mode with Cloud Resource Library. If so, we enable to the
   * Janitor to auto-delete all created cloud resources.
   */
  private boolean testingMode = false;

  /** Credential file path to be able to publish message to Janitor */
  private String janitorClientCredentialFilePath;

  /** pubsub project id to publish track resource to Janitor */
  private String janitorTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor */
  private String janitorTrackResourceTopicId;

  public void setTestingMode(boolean testingMode) {
    this.testingMode = testingMode;
  }

  public void setJanitorClientCredentialFilePath(String janitorClientCredentialFilePath) {
    this.janitorClientCredentialFilePath = janitorClientCredentialFilePath;
  }

  public void setJanitorTrackResourceProjectId(String janitorTrackResourceProjectId) {
    this.janitorTrackResourceProjectId = janitorTrackResourceProjectId;
  }

  public void setJanitorTrackResourceTopicId(String janitorTrackResourceTopicId) {
    this.janitorTrackResourceTopicId = janitorTrackResourceTopicId;
  }

  /**
   * The {@link ClientConfig} in CRL's COW object. If in test, it will also include {@link
   * CleanupConfig}.
   */
  @Bean
  @Lazy
  public ClientConfig clientConfig() {
    ClientConfig.Builder builder = ClientConfig.Builder.newBuilder().setClient(CLIENT_NAME);
    if (testingMode) {
      builder.setCleanupConfig(
          CleanupConfig.builder()
              .setCleanupId(CLIENT_NAME + "-test")
              .setJanitorProjectId(janitorTrackResourceProjectId)
              .setTimeToLive(TEST_RESOURCE_TIME_TO_LIVE)
              .setJanitorTopicName(janitorTrackResourceTopicId)
              .setCredentials(getGoogleCredentialsOrDie(janitorClientCredentialFilePath))
              .build());
    }
    return builder.build();
  }

  /** The CRL {@link CloudResourceManagerCow} which wrappers Google Cloud Resource Manager API. */
  @Bean
  public CloudResourceManagerCow cloudResourceManagerCow()
      throws IOException, GeneralSecurityException {
    return CloudResourceManagerCow.create(
        clientConfig(), GoogleCredentials.getApplicationDefault());
  }

  /** The CRL {@link CloudBillingClientCow} which wrappers Google Billing API. */
  @Bean
  public CloudBillingClientCow cloudBillingClientCow() throws IOException {
    return new CloudBillingClientCow(clientConfig(), GoogleCredentials.getApplicationDefault());
  }

  /** The CRL {@link ServiceUsageCow} which wrappers Google Cloud ServiceUsage API. */
  @Bean
  public ServiceUsageCow serviceUsageCow() throws GeneralSecurityException, IOException {
    return ServiceUsageCow.create(clientConfig(), GoogleCredentials.getApplicationDefault());
  }

  private static ServiceAccountCredentials getGoogleCredentialsOrDie(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread().getContextClassLoader().getResourceAsStream(serviceAccountPath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load GoogleCredentials from configuration" + serviceAccountPath, e);
    }
  }
}
