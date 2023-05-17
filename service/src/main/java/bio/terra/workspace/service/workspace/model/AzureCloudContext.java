package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.CloudContextNotReadyException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import org.jetbrains.annotations.Nullable;

public class AzureCloudContext implements CloudContext {
  private final AzureCloudContextFields contextFields;
  private final CloudContextCommonFields commonFields;

  @JsonCreator
  public AzureCloudContext(
      @JsonProperty("contextFields") @Nullable AzureCloudContextFields contextFields,
      @JsonProperty("commonFields") CloudContextCommonFields commonFields) {
    this.contextFields = contextFields;
    this.commonFields = commonFields;
  }

  public AzureCloudContextFields getContextFields() {
    return contextFields;
  }

  // HYPOTHESIS: everywhere we get this data, the context must be in the READY state
  public String getAzureTenantId() {
    checkReady();
    return contextFields.getAzureTenantId();
  }

  public String getAzureSubscriptionId() {
    checkReady();
    return contextFields.getAzureSubscriptionId();
  }

  public String getAzureResourceGroupId() {
    checkReady();
    return contextFields.getAzureResourceGroupId();
  }

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AZURE;
  }

  @Override
  public @Nullable CloudContextCommonFields getCommonFields() {
    return commonFields;
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
  public ApiAzureContext toApi() {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AzureCloudContext that)) return false;
    return Objects.equal(contextFields, that.contextFields)
        && Objects.equal(commonFields, that.commonFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(contextFields, commonFields);
  }

  // -- serdes for the AzureCloudContext --

  @Override
  public String serialize() {
    if (contextFields == null) {
      throw new InternalLogicException("Cannot serialize without context fields filled in");
    }
    return contextFields.serialize();
  }

  public static AzureCloudContext deserialize(DbCloudContext dbCloudContext) {
    AzureCloudContextFields contextFields =
        (dbCloudContext.getContextJson() == null
            ? null
            : AzureCloudContextFields.deserialize(dbCloudContext.getContextJson()));

    return new AzureCloudContext(
        contextFields,
        new CloudContextCommonFields(
            dbCloudContext.getSpendProfile(),
            dbCloudContext.getState(),
            dbCloudContext.getFlightId(),
            dbCloudContext.getError()));
  }
}
