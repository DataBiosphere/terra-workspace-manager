package bio.terra.workspace.app.configuration.external;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.aws")
public class AwsConfiguration {

  private String defaultLandingZone;
  private String googleJwtAudience;

  private List<AwsLandingZoneConfiguration> landingZones;

  public String getDefaultLandingZone() {
    return defaultLandingZone;
  }

  public void setDefaultLandingZone(String defaultLandingZone) {
    this.defaultLandingZone = defaultLandingZone;
  }

  public String getGoogleJwtAudience() {
    return googleJwtAudience;
  }

  public void setGoogleJwtAudience(String googleJwtAudience) {
    this.googleJwtAudience = googleJwtAudience;
  }

  public List<AwsLandingZoneConfiguration> getLandingZones() {
    return landingZones;
  }

  public void setLandingZones(List<AwsLandingZoneConfiguration> landingZones) {
    this.landingZones = landingZones;
  }

  public static class AwsLandingZoneBucket {
    private String name;
    private String region;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }
  }

  public static class AwsLandingZoneConfiguration {
    private String name;
    private String accountNumber;
    private String serviceRoleArn;
    private String userRoleArn;
    private String notebookLifecycleConfigArn;
    private List<AwsLandingZoneBucket> buckets;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getAccountNumber() {
      return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
      this.accountNumber = accountNumber;
    }

    public String getServiceRoleArn() {
      return serviceRoleArn;
    }

    public void setServiceRoleArn(String serviceRoleArn) {
      this.serviceRoleArn = serviceRoleArn;
    }

    public String getUserRoleArn() {
      return userRoleArn;
    }

    public void setUserRoleArn(String userRoleArn) {
      this.userRoleArn = userRoleArn;
    }

    public String getNotebookLifecycleConfigArn() {
      return notebookLifecycleConfigArn;
    }

    public void setNotebookLifecycleConfigArn(String notebookLifecycleConfigArn) {
      this.notebookLifecycleConfigArn = notebookLifecycleConfigArn;
    }

    public List<AwsLandingZoneBucket> getBuckets() {
      return buckets;
    }

    public void setBuckets(List<AwsLandingZoneBucket> buckets) {
      this.buckets = buckets;
    }
  }
}
