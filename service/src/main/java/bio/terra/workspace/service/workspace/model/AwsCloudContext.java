package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class AwsCloudContext {
  private String majorVersion;
  private String organizationId;
  private String accountId;
  private String tenantAlias;
  private String environmentAlias;

  // Constructor for Jackson
  public AwsCloudContext() {}

  // Constructor for V1
  public AwsCloudContext(
      String majorVersion,
      String organizationId,
      String accountId,
      String tenantAlias,
      String environmentAlias) {
    this.majorVersion = majorVersion;
    this.organizationId = organizationId;
    this.accountId = accountId;
    this.tenantAlias = tenantAlias;
    this.environmentAlias = environmentAlias;
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

  public ApiAwsContext toApi() {
    return new ApiAwsContext()
        .majorVersion(majorVersion)
        .organizationId(organizationId)
        .accountId(accountId)
        .tenantAlias(tenantAlias)
        .environmentAlias(environmentAlias);
  }

  public static AwsCloudContext fromApi(ApiAwsContext awsContext) {
    return new AwsCloudContext(
        awsContext.getMajorVersion(),
        awsContext.getOrganizationId(),
        awsContext.getAccountId(),
        awsContext.getTenantAlias(),
        awsContext.getEnvironmentAlias());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AwsCloudContext that = (AwsCloudContext) o;

    return new EqualsBuilder()
        .append(majorVersion, that.majorVersion)
        .append(organizationId, that.organizationId)
        .append(accountId, that.accountId)
        .append(tenantAlias, that.tenantAlias)
        .append(environmentAlias, that.environmentAlias)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(majorVersion)
        .append(organizationId)
        .append(accountId)
        .append(tenantAlias)
        .append(environmentAlias)
        .toHashCode();
  }

  public String serialize() {
    AwsCloudContextV1 dbContext = new AwsCloudContextV1(this);
    return DbSerDes.toJson(dbContext);
  }

  public static @Nullable AwsCloudContext deserialize(@Nullable String json) {
    if (json == null) {
      return null;
    }

    try {
      AwsCloudContextV1 dbContext = DbSerDes.fromJson(json, AwsCloudContextV1.class);
      dbContext.validateVersion();

      return new AwsCloudContext(
          dbContext.majorVersion,
          dbContext.organizationId,
          dbContext.accountId,
          dbContext.tenantAlias,
          dbContext.environmentAlias);

    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version: " + e);
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

    @JsonCreator
    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("majorVersion") String majorVersion,
        @JsonProperty("organizationId") String organizationId,
        @JsonProperty("accountId") String accountId,
        @JsonProperty("tenantAlias") String tenantAlias,
        @JsonProperty("environmentAlias") String environmentAlias) {
      this.version = version;
      this.majorVersion = majorVersion;
      this.organizationId = organizationId;
      this.accountId = accountId;
      this.tenantAlias = tenantAlias;
      this.environmentAlias = environmentAlias;
    }

    public AwsCloudContextV1(AwsCloudContext awsCloudContext) {
      this.version = getVersion();
      this.majorVersion = awsCloudContext.majorVersion;
      this.organizationId = awsCloudContext.organizationId;
      this.accountId = awsCloudContext.accountId;
      this.tenantAlias = awsCloudContext.tenantAlias;
      this.environmentAlias = awsCloudContext.environmentAlias;
    }

    public void validateVersion() {
      if (this.version != getVersion()) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextV1");
      }
    }

    public static long getVersion() {
      return AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
    }
  }
}
