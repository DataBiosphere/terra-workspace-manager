package bio.terra.workspace.app.configuration.external;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spend Profiles.
 *
 * <p>TODO: Replace the configuration based Spend Profile with integration with the to-be-built
 * Spend Profile Component. For now, we manage spend profiles manually.
 */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.spend")
public class SpendProfileConfiguration {
  /** The Spend Profiles known to Workspace Manager. */
  private List<SpendProfileModel> spendProfiles = new ArrayList<>();

  /** URL of the billing profile manager instance */
  private String basePath;

  public List<SpendProfileModel> getSpendProfiles() {
    return ImmutableList.copyOf(spendProfiles);
  }

  public void setSpendProfiles(List<SpendProfileModel> spendProfiles) {
    this.spendProfiles = spendProfiles;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  /** Configuration class for specifying a Spend Profile. */
  public static class SpendProfileModel {
    /** The id of the Spend Profile. */
    private String id;

    /** The id of the Google Billing Account associated with the Spend Profile. */
    private String billingAccountId;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getBillingAccountId() {
      return billingAccountId;
    }

    public void setBillingAccountId(String billingAccountId) {
      this.billingAccountId = billingAccountId;
    }
  }
}
