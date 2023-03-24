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

  @Override
  public String toString() {
    return String.format(
        "{authentication=%s, discovery=%s}", authentication.toString(), discovery.toString());
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

    @Override
    public String toString() {
      return String.format(
          "{bucket=%s, caching=%s, roleArn=\"%s\"}",
          bucket.toString(), caching.toString(), roleArn);
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

      @Override
      public String toString() {
        return String.format("{name=\"%s\", region=\"%s\"}", name, region.toString());
      }
    }

    public static class Caching {
      public static final boolean DEFAULT_ENABLED = true;
      public static final long DEFAULT_EXPIRATION_TIME_SECONDS = 600;

      private boolean enabled = DEFAULT_ENABLED;
      private long expirationTimeSeconds = DEFAULT_EXPIRATION_TIME_SECONDS;

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

      @Override
      public String toString() {
        return String.format(
            "{enabled=%b, expirationTimeSeconds=%d}", enabled, expirationTimeSeconds);
      }
    }
  }

  public static class Authentication {
    public static final long DEFAULT_CREDENTIAL_LIFETIME_SECONDS = 900;
    public static final long DEFAULT_CREDENTIAL_STALE_TIME_SECONDS = 300;

    private String googleJwtAudience;
    private long credentialLifetimeSeconds = DEFAULT_CREDENTIAL_LIFETIME_SECONDS;
    private long credentialStaleTimeSeconds = DEFAULT_CREDENTIAL_STALE_TIME_SECONDS;

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

    @Override
    public String toString() {
      return String.format(
          "{credentialLifetimeSeconds=%d, credentialStaleTimeSeconds=%d, googleJwtAudience=\"%s\"}",
          credentialLifetimeSeconds, credentialStaleTimeSeconds, googleJwtAudience);
    }
  }
}
