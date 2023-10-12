package bio.terra.workspace.service.workspace.model;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import java.util.Map;
import javax.annotation.Nullable;

public class AwsCloudContextFields {
  private final String majorVersion;
  private final String organizationId;
  private final String accountId;
  private final String tenantAlias;
  private final String environmentAlias;
  private Map<String, String> applicationSecurityGroups;

  @JsonCreator
  public AwsCloudContextFields(
      @JsonProperty("majorVersion") String majorVersion,
      @JsonProperty("organizationId") String organizationId,
      @JsonProperty("accountId") String accountId,
      @JsonProperty("tenantAlias") String tenantAlias,
      @JsonProperty("environmentAlias") String environmentAlias,
      @JsonProperty("applicationSecurityGroups") @Nullable
          Map<String, String> applicationSecurityGroups) {
    this.majorVersion = majorVersion;
    this.organizationId = organizationId;
    this.accountId = accountId;
    this.tenantAlias = tenantAlias;
    this.environmentAlias = environmentAlias;
    this.applicationSecurityGroups = applicationSecurityGroups;
  }

  public String getMajorVersion() {
    return majorVersion;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getTenantAlias() {
    return tenantAlias;
  }

  public String getEnvironmentAlias() {
    return environmentAlias;
  }

  public @Nullable Map<String, String> getApplicationSecurityGroups() {
    return applicationSecurityGroups;
  }

  public void toApi(ApiAwsContext awsContext) {
    awsContext
        .majorVersion(majorVersion)
        .organizationId(organizationId)
        .accountId(accountId)
        .tenantAlias(tenantAlias)
        .environmentAlias(environmentAlias)
        .applicationSecurityGroups(applicationSecurityGroups);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AwsCloudContextFields that)) return false;
    return Objects.equal(majorVersion, that.majorVersion)
        && Objects.equal(organizationId, that.organizationId)
        && Objects.equal(accountId, that.accountId)
        && Objects.equal(tenantAlias, that.tenantAlias)
        && Objects.equal(environmentAlias, that.environmentAlias)
        && Objects.equal(applicationSecurityGroups, that.applicationSecurityGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        majorVersion,
        organizationId,
        accountId,
        tenantAlias,
        environmentAlias,
        applicationSecurityGroups);
  }

  /**
   * Verifies that current cloud context matches the current environment
   *
   * @param environment discovery environment
   * @throws StaleConfigurationException StaleConfigurationException if they do not match
   */
  public void verifyCloudContextFields(Environment environment) {
    Metadata metadata = environment.getMetadata();
    if (!majorVersion.equals(metadata.getMajorVersion())
        || !accountId.equals(metadata.getAccountId())) {
      throw new StaleConfigurationException(
          String.format(
              "AWS cloud context mismatch. Metadata version is %s, expected %s; Account id is %s, expected %s",
              majorVersion, metadata.getMajorVersion(), accountId, metadata.getAccountId()));
    }
  }

  public String serialize() {
    AwsCloudContextV1 dbContext = new AwsCloudContextV1(this);
    return DbSerDes.toJson(dbContext);
  }

  public static AwsCloudContextFields deserialize(String contextJson) {
    try {
      AwsCloudContextV1 dbContext = DbSerDes.fromJson(contextJson, AwsCloudContextV1.class);
      dbContext.validateVersion();

      return new AwsCloudContextFields(
          dbContext.majorVersion,
          dbContext.organizationId,
          dbContext.accountId,
          dbContext.tenantAlias,
          dbContext.environmentAlias,
          dbContext.applicationSecurityGroups);

    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version", e);
    }
  }

  /** Mask AWS version numbers so as not to collide with Azure and GCP version numbers */
  public static final long AWS_CLOUD_CONTEXT_DB_VERSION_MASK = 0x100;

  @VisibleForTesting
  public static class AwsCloudContextV1 {
    private static final long AWS_CLOUD_CONTEXT_DB_VERSION = 1;

    public long version;
    public String majorVersion;
    public String organizationId;
    public String accountId;
    public String tenantAlias;
    public String environmentAlias;
    public Map<String, String> applicationSecurityGroups;

    @JsonCreator
    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("majorVersion") String majorVersion,
        @JsonProperty("organizationId") String organizationId,
        @JsonProperty("accountId") String accountId,
        @JsonProperty("tenantAlias") String tenantAlias,
        @JsonProperty("environmentAlias") String environmentAlias,
        @JsonProperty("applicationSecurityGroups") Map<String, String> applicationSecurityGroups) {
      this.version = version;
      this.majorVersion = majorVersion;
      this.organizationId = organizationId;
      this.accountId = accountId;
      this.tenantAlias = tenantAlias;
      this.environmentAlias = environmentAlias;
      this.applicationSecurityGroups = applicationSecurityGroups;
    }

    public AwsCloudContextV1(AwsCloudContextFields awsCloudContext) {
      this.version = getVersion();
      this.majorVersion = awsCloudContext.majorVersion;
      this.organizationId = awsCloudContext.organizationId;
      this.accountId = awsCloudContext.accountId;
      this.tenantAlias = awsCloudContext.tenantAlias;
      this.environmentAlias = awsCloudContext.environmentAlias;
      this.applicationSecurityGroups = awsCloudContext.applicationSecurityGroups;
    }

    public static long getVersion() {
      return (AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK);
    }

    public void validateVersion() {
      if (this.version != getVersion()) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextV1");
      }
    }
  }
}
