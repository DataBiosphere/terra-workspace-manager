package bio.terra.workspace.app.configuration.external;

import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  private List<SpendProfile> spendProfiles = new ArrayList<>();

  /** URL of the billing profile manager instance */
  private String basePath;

  public List<SpendProfile> getSpendProfiles() {
    return ImmutableList.copyOf(spendProfiles);
  }

  public void setSpendProfiles(List<SpendProfile> spendProfiles) {
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

    private Map<String, String> limits;

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

    public Map<String, String>  getLimits(){
      return limits;
    }

    public void setLimits(Map<String, String> limits){
      this.limits = limits;
    }
  }
}
