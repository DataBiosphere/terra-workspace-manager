package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAzureContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

  @JsonIgnore
  public String getAzureTenantId() {
    return contextFields.getAzureTenantId();
  }

  @JsonIgnore
  public String getAzureSubscriptionId() {
    return contextFields.getAzureSubscriptionId();
  }

  @JsonIgnore
  public String getAzureResourceGroupId() {
    return contextFields.getAzureResourceGroupId();
  }

  @Override
  @JsonIgnore
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AZURE;
  }

  @Override
  public @Nullable CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  public ApiAzureContext toApi() {
    var azureContext = new ApiAzureContext();
    azureContext.operationState(commonFields.toApi());
    if (contextFields != null) {
      contextFields.toApi(azureContext);
    }
    return azureContext;
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
