package bio.terra.workspace.service.workspace.model;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.exception.StaleConfigurationException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.CloudContextNotReadyException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
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

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AWS;
  }

  @Override
  public CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  public AwsCloudContextFields getContextFields() {
    return contextFields;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(CloudPlatform cloudPlatform) {
    if (cloudPlatform != getCloudPlatform()) {
      throw new InternalLogicException(
          String.format("Invalid cast from %s to %s", getCloudPlatform(), cloudPlatform));
    }
    return (T) this;
  }

  // TODO: PF-2770 include the common fields in the API return
  public ApiAwsContext toApi() {
    return (contextFields == null ? null : contextFields.toApi());
  }

  /**
   * Test if the cloud context is ready to be used by operations. It must be in the ready state and
   * the context fields must be populated.
   *
   * @return true if the context is in the READY state; false otherwise
   */
  public boolean isReady() {
    return (commonFields.state().equals(WsmResourceState.READY) && contextFields != null);
  }

  /** Throw exception is the cloud context is not ready. */
  public void checkReady() {
    if (!isReady()) {
      throw new CloudContextNotReadyException(
          "Cloud context is not ready. Wait for the context to be ready and try again.");
    }
  }

  /**
   * Verifies that current cloud context is the same as the expected cloud context by compares only
   * relevant fields
   *
   * @param environment expected environment
   * @throws StaleConfigurationException StaleConfigurationException if they do not match
   */
  public void verifyCloudContext(Environment environment) {
    checkReady();
    contextFields.verifyCloudContext(environment);
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
