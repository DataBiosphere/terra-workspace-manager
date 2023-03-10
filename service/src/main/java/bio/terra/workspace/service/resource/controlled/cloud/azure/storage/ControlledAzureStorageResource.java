package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureStorageAttributes;
import bio.terra.workspace.generated.model.ApiAzureStorageResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import java.util.Optional;

public class ControlledAzureStorageResource extends ControlledResource {
  private final String storageAccountName;

  @JsonCreator
  public ControlledAzureStorageResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("storageAccountName") String storageAccountName) {
    super(resourceFields, controlledResourceFields);
    this.storageAccountName = storageAccountName;
    validate();
  }

  private ControlledAzureStorageResource(
      ControlledResourceFields common, String storageAccountName) {
    super(common);
    this.storageAccountName = storageAccountName;
    validate();
  }

  public static ControlledAzureStorageResource.Builder builder() {
    return new ControlledAzureStorageResource.Builder();
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  // -- getters used in serialization --

  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @Override
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  // -- getters not included in serialization --

  @JsonIgnore
  public String getStorageAccountEndpoint() {
    return String.format(Locale.ROOT, "https://%s.blob.core.windows.net", storageAccountName);
  }

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_STORAGE_ACCOUNT;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("storageAccountName", getStorageAccountName()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();
    flight.addStep(
        new GetAzureStorageStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureStorageStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getStorageAccountKeyProvider()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureStorageStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureStorageAttributes toApiAttributes() {
    return new ApiAzureStorageAttributes()
        .storageAccountName(getStorageAccountName())
        .region(getRegion());
  }

  public ApiAzureStorageResource toApiResource() {
    return new ApiAzureStorageResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureStorageAttributes(getStorageAccountName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureStorage(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT
        || getResourceFamily() != WsmResourceFamily.AZURE_STORAGE_ACCOUNT
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AZURE_STORAGE_ACCOUNT");
    }
    if (getStorageAccountName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required storage account name field for ControlledAzureStorage.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureStorage.");
    }
    ResourceValidationUtils.validateStorageAccountName(getStorageAccountName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureStorageResource that = (ControlledAzureStorageResource) o;

    return storageAccountName.equals(that.getStorageAccountName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 42 * result + storageAccountName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String storageAccountName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureStorageResource.Builder storageAccountName(String storageAccountName) {
      this.storageAccountName = storageAccountName;
      return this;
    }

    public ControlledAzureStorageResource build() {
      return new ControlledAzureStorageResource(common, storageAccountName);
    }
  }
}
