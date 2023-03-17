package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Please see "Runtime Configuration" section of AWS.md in the root of this repository for more
 * details.
 */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.aws")
public class AwsConfiguration {

  private Discovery discovery;
  private Authentication authentication;

  public Discovery getDiscovery() {
    return discovery;
  }

  public void setDiscovery(Discovery discovery) {
    this.discovery = discovery;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public static class Discovery {
    private String roleArn;
    private Bucket bucket;
    private Caching caching = new Caching();

    public String getRoleArn() {
      return roleArn;
    }

    public void setRoleArn(String roleArn) {
      this.roleArn = roleArn;
    }

    public Bucket getBucket() {
      return bucket;
    }

    public void setBucket(Bucket bucket) {
      this.bucket = bucket;
    }

    public Caching getCaching() {
      return caching;
    }

    public void setCaching(Caching caching) {
      this.caching = caching;
    }

    public static class Bucket {

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

    public static class Caching {
      private boolean enabled = true;
      private long expirationTimeSeconds = 600;

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(boolean enabled) {
        this.enabled = enabled;
      }

      public long getExpirationTimeSeconds() {
        return expirationTimeSeconds;
      }

      public void setExpirationTimeSeconds(long expirationTimeSeconds) {
        this.expirationTimeSeconds = expirationTimeSeconds;
      }
    }
  }

  public static class Authentication {
    private String googleJwtAudience;
    private long credentialLifetimeSeconds = 900;
    private long credentialStaleTimeSeconds = 300;

    public String getGoogleJwtAudience() {
      return googleJwtAudience;
    }

    public void setGoogleJwtAudience(String googleJwtAudience) {
      this.googleJwtAudience = googleJwtAudience;
    }

    public long getCredentialLifetimeSeconds() {
      return credentialLifetimeSeconds;
    }

    public void setCredentialLifetimeSeconds(long credentialLifetimeSeconds) {
      this.credentialLifetimeSeconds = credentialLifetimeSeconds;
    }

    public long getCredentialStaleTimeSeconds() {
      return credentialStaleTimeSeconds;
    }

    public void setCredentialStaleTimeSeconds(long credentialStaleTimeSeconds) {
      this.credentialStaleTimeSeconds = credentialStaleTimeSeconds;
    }
  }
}
