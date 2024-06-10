package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Configuration to use Terra Cloud Resource Library. */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.crl")
public class CrlConfiguration {
  /**
   * Whether to enable the use of the Cloud Resource Library. It is disabled for unit testing. The
   * default for all other testing and production is true.
   */
  private boolean useCrl = true;

  /**
   * Whether to enable the use of the Janitor by the Cloud Resource Library to auto-delete all
   * created cloud resources. It is set to true for connected and integration testing. The default
   * for production is false.
   */
  private boolean useJanitor = false;

  /** Credential file path to be able to publish message to Janitor */
  private String janitorClientCredentialFilePath;

  /** pubsub project id to publish track resource to Janitor */
  private String janitorTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor */
  private String janitorTrackResourceTopicId;

  /** Number of hours before Janitor cleans up a tracked resource */
  private int janitorTtlHours = 1;

  public boolean getUseCrl() {
    return useCrl;
  }

  public void setUseCrl(boolean useCrl) {
    this.useCrl = useCrl;
  }

  public boolean useJanitor() {
    return useJanitor;
  }

  public String getJanitorClientCredentialFilePath() {
    return janitorClientCredentialFilePath;
  }

  public String getJanitorTrackResourceProjectId() {
    return janitorTrackResourceProjectId;
  }

  public String getJanitorTrackResourceTopicId() {
    return janitorTrackResourceTopicId;
  }

  public int getJanitorTtlHours() {
    return janitorTtlHours;
  }

  public void setUseJanitor(boolean useJanitor) {
    this.useJanitor = useJanitor;
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

  public void setJanitorTtlHours(int janitorTtlHours) {
    this.janitorTtlHours = janitorTtlHours;
  }
}
