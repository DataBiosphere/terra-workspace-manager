package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureManagedIdentityAttributes;
import bio.terra.workspace.generated.model.ApiAzureManagedIdentityResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ControlledAzureManagedIdentityResource extends ControlledResource {
  private final String managedIdentityName;

  @JsonCreator
  public ControlledAzureManagedIdentityResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("managedIdentityName") String managedIdentityName) {
    super(resourceFields, controlledResourceFields);
    this.managedIdentityName = managedIdentityName;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureManagedIdentityResource(
      ControlledResourceFields common, String managedIdentityName) {
    super(common);
    this.managedIdentityName = managedIdentityName;
    validate();
  }

  public static Builder builder() {
    return new Builder();
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

  public String getManagedIdentityName() {
    return managedIdentityName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_MANAGED_IDENTITY;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("managedIdentityName", getManagedIdentityName()));
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
        new AzureManagedIdentityGuardStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureManagedIdentityStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(
        new DeleteAzureManagedIdentityStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this));
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureManagedIdentityResource toApiResource() {
    return new ApiAzureManagedIdentityResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  private ApiAzureManagedIdentityAttributes toApiAttributes() {
    return new ApiAzureManagedIdentityAttributes().managedIdentityName(getManagedIdentityName());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureManagedIdentityAttributes(getManagedIdentityName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureManagedIdentity(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY
        || getResourceFamily() != WsmResourceFamily.AZURE_MANAGED_IDENTITY
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_MANAGED_IDENTITY");
    }
    if (getManagedIdentityName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required managedIdentityName field for ControlledAzureManagedIdentity.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureManagedIdentity.");
    }
    AzureResourceValidationUtils.validateAzureManagedIdentityName(getManagedIdentityName());
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && partialEqual(o);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ControlledAzureManagedIdentityResource that = (ControlledAzureManagedIdentityResource) o;
    return Objects.equals(managedIdentityName, that.managedIdentityName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), managedIdentityName);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String managedIdentityName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder managedIdentityName(String managedIdentityName) {
      this.managedIdentityName = managedIdentityName;
      return this;
    }

    public ControlledAzureManagedIdentityResource build() {
      return new ControlledAzureManagedIdentityResource(common, managedIdentityName);
    }
  }
}
