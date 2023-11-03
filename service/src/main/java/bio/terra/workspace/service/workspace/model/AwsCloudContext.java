package bio.terra.workspace.service.workspace.model;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidCloudContextStateException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import java.util.Map;
import javax.annotation.Nullable;

public class AwsCloudContext implements CloudContext {
  private final @Nullable AwsCloudContextFields contextFields;
  private final CloudContextCommonFields commonFields;

  @JsonCreator
  public AwsCloudContext(
      @JsonProperty("contextFields") @Nullable AwsCloudContextFields contextFields,
      @JsonProperty("commonFields") CloudContextCommonFields commonFields) {
    this.contextFields = contextFields;
    this.commonFields = commonFields;
  }

  @JsonIgnore
  public String getMajorVersion() {
    return contextFields.getMajorVersion();
  }

  @JsonIgnore
  public String getOrganizationId() {
    return contextFields.getOrganizationId();
  }

  @JsonIgnore
  public String getAccountId() {
    return contextFields.getAccountId();
  }

  @JsonIgnore
  public String getTenantAlias() {
    return contextFields.getTenantAlias();
  }

  @JsonIgnore
  public String getEnvironmentAlias() {
    return contextFields.getEnvironmentAlias();
  }

  @JsonIgnore
  @Nullable
  public Map<String, String> getApplicationSecurityGroups() {
    return contextFields.getApplicationSecurityGroups();
  }

  @Override
  @JsonIgnore
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AWS;
  }

  @Override
  public CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  public @Nullable AwsCloudContextFields getContextFields() {
    return contextFields;
  }

  public ApiAwsContext toApi() {
    var awsContext = new ApiAwsContext();
    awsContext.operationState(commonFields.toApi());
    if (contextFields != null) {
      contextFields.toApi(awsContext);
    }
    return awsContext;
  }

  /**
   * Verifies that current cloud context is the same as the expected cloud context by compares only
   * relevant fields
   *
   * @param environment expected environment
   * @throws StaleConfigurationException StaleConfigurationException if they do not match
   */
  public void verifyCloudContext(Environment environment) {
    if (contextFields == null) {
      throw new InvalidCloudContextStateException(
          "Cloud context is not in a valid state. Wait and try again.");
    }
    contextFields.verifyCloudContextFields(environment);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AwsCloudContext that)) return false;
    return Objects.equal(contextFields, that.contextFields)
        && Objects.equal(commonFields, that.commonFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(contextFields, commonFields);
  }

  @Override
  public String serialize() {
    if (contextFields == null) {
      throw new InternalLogicException("Cannot serialize without context fields filled in");
    }
    return contextFields.serialize();
  }

  public static AwsCloudContext deserialize(DbCloudContext dbCloudContext) {
    AwsCloudContextFields contextFields =
        (dbCloudContext.getContextJson() == null
            ? null
            : AwsCloudContextFields.deserialize(dbCloudContext.getContextJson()));

    return new AwsCloudContext(
        contextFields,
        new CloudContextCommonFields(
            dbCloudContext.getSpendProfile(),
            dbCloudContext.getState(),
            dbCloudContext.getFlightId(),
            dbCloudContext.getError()));
  }
}
