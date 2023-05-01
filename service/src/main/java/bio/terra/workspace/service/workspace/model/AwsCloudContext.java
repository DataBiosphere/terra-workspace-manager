package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;

import com.google.common.base.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class AwsCloudContext implements CloudContext {
  private final String majorVersion;
  private final String organizationId;
  private final String accountId;
  private final String tenantAlias;
  private final String environmentAlias;
  private final @Nullable CloudContextCommonFields commonFields;

  @JsonCreator
  public AwsCloudContext(
    @JsonProperty("majorVersion") String majorVersion,
    @JsonProperty("organizationId") String organizationId,
    @JsonProperty("accountId") String accountId,
    @JsonProperty("tenantAlias") String tenantAlias,
    @JsonProperty("environmentAlias") String environmentAlias,
    @JsonProperty("commonFields") CloudContextCommonFields commonFields) {
    this.majorVersion = majorVersion;
    this.organizationId = organizationId;
    this.accountId = accountId;
    this.tenantAlias = tenantAlias;
    this.environmentAlias = environmentAlias;
    this.commonFields = commonFields;
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

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AWS;
  }

  @Override
  public @Nullable CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(CloudPlatform cloudPlatform) {
    if (cloudPlatform != getCloudPlatform()) {
      throw new InternalLogicException(String
        .format("Invalid cast from %s to %s", getCloudPlatform(), cloudPlatform));
    }
    return (T) this;
  }

  public ApiAwsContext toApi() {
    return new ApiAwsContext()
        .majorVersion(majorVersion)
        .organizationId(organizationId)
        .accountId(accountId)
        .tenantAlias(tenantAlias)
        .environmentAlias(environmentAlias);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AwsCloudContext that)) return false;
    return Objects.equal(majorVersion, that.majorVersion) && Objects.equal(organizationId, that.organizationId) && Objects.equal(accountId, that.accountId) && Objects.equal(tenantAlias, that.tenantAlias) && Objects.equal(environmentAlias, that.environmentAlias) && Objects.equal(commonFields, that.commonFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(majorVersion, organizationId, accountId, tenantAlias, environmentAlias, commonFields);
  }

  public String serialize() {
    AwsCloudContextV1 dbContext = new AwsCloudContextV1(this);
    return DbSerDes.toJson(dbContext);
  }

  public static AwsCloudContext deserialize(DbCloudContext dbCloudContext) {
    try {
      AwsCloudContextV1 dbContext = DbSerDes.fromJson(dbCloudContext.getContextJson(), AwsCloudContextV1.class);
      dbContext.validateVersion();

      return new AwsCloudContext(
        dbContext.majorVersion,
        dbContext.organizationId,
        dbContext.accountId,
        dbContext.tenantAlias,
        dbContext.environmentAlias,
        new CloudContextCommonFields(
          dbCloudContext.getSpendProfile(),
          dbCloudContext.getState(),
          dbCloudContext.getFlightId(),
          dbCloudContext.getError()));

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
      this.version = AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
      this.majorVersion = awsCloudContext.majorVersion;
      this.organizationId = awsCloudContext.organizationId;
      this.accountId = awsCloudContext.accountId;
      this.tenantAlias = awsCloudContext.tenantAlias;
      this.environmentAlias = awsCloudContext.environmentAlias;
    }

    public void validateVersion() {
      if (this.version != (AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK)) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextV1");
      }
    }
  }
}
