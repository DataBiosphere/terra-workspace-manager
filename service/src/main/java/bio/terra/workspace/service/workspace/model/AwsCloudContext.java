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
  private String awsOrganization;
  private String awsAccountNumber;
  private String tenantId;
  private String terraEnvironment;

  // Constructor for Jackson
  public AwsCloudContext() {}

  // Constructor for V1
  public AwsCloudContext(
      String awsOrganization, String awsAccountNumber, String tenantId, String terraEnvironment) {
    this.awsOrganization = awsOrganization;
    this.awsAccountNumber = awsAccountNumber;
    this.tenantId = tenantId;
    this.terraEnvironment = terraEnvironment;
  }

  public String getAwsOrganization() {
    return awsOrganization;
  }

  public String getAwsAccountNumber() {
    return awsAccountNumber;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getTerraEnvironment() {
    return terraEnvironment;
  }

  public ApiAwsContext toApi() {
    return new ApiAwsContext()
        .awsOrganization(awsOrganization)
        .awsAccountNumber(awsAccountNumber)
        .tenantId(tenantId)
        .terraEnvironment(terraEnvironment);
  }

  public static AwsCloudContext fromApi(ApiAwsContext awsContext) {
    return new AwsCloudContext(
        awsContext.getAwsOrganization(),
        awsContext.getAwsAccountNumber(),
        awsContext.getTenantId(),
        awsContext.getTerraEnvironment());
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
        .append(awsOrganization, that.awsOrganization)
        .append(awsAccountNumber, that.awsAccountNumber)
        .append(tenantId, that.tenantId)
        .append(terraEnvironment, that.terraEnvironment)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(awsOrganization)
        .append(awsAccountNumber)
        .append(tenantId)
        .append(terraEnvironment)
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
          dbContext.awsOrganization,
          dbContext.awsAccountNumber,
          dbContext.tenantId,
          dbContext.terraEnvironment);

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
    public String awsOrganization;
    public String awsAccountNumber;
    public String tenantId;
    public String terraEnvironment;

    @JsonCreator
    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("awsOrganization") String awsOrganization,
        @JsonProperty("awsAccountNumber") String awsAccountNumber,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("terraEnvironment") String terraEnvironment) {
      this.version = version;
      this.awsOrganization = awsOrganization;
      this.awsAccountNumber = awsAccountNumber;
      this.tenantId = tenantId;
      this.terraEnvironment = terraEnvironment;
    }

    public AwsCloudContextV1(AwsCloudContext awsCloudContext) {
      this.version = AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
      this.awsOrganization = awsCloudContext.awsOrganization;
      this.awsAccountNumber = awsCloudContext.awsAccountNumber;
      this.tenantId = awsCloudContext.tenantId;
      this.terraEnvironment = awsCloudContext.terraEnvironment;
    }

    public void validateVersion() {
      if (this.version != (AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK)) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextV1");
      }
    }
  }
}
