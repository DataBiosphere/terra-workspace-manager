package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceAttributes;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
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
import java.util.Optional;

public class ControlledAzureRelayNamespaceResource extends ControlledResource {
  private final String namespaceName;

  @JsonCreator
  public ControlledAzureRelayNamespaceResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("namespaceName") String namespaceName) {
    super(resourceFields, controlledResourceFields);
    this.namespaceName = namespaceName;
    validate();
  }

  public ControlledAzureRelayNamespaceResource(
      ControlledResourceFields common, String namespaceName) {
    super(common);
    this.namespaceName = namespaceName;
    validate();
  }

  public static Builder builder() {
    return new Builder();
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

  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_RELAY_NAMESPACE;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("Name", getName()));
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
        new GetAzureRelayNamespaceStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureRelayNamespaceStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureRelayNamespaceStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public ApiAzureRelayNamespaceAttributes toApiAttributes() {
    return new ApiAzureRelayNamespaceAttributes()
        .namespaceName(getNamespaceName())
        .region(getRegion());
  }

  public ApiAzureRelayNamespaceResource toApiResource() {
    return new ApiAzureRelayNamespaceResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureRelayNamespaceAttributes(getNamespaceName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureRelayNamespace(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE
        || getResourceFamily() != WsmResourceFamily.AZURE_RELAY_NAMESPACE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_RELAY_NAMESPACE");
    }
    if (getName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required Name field for ControlledAzureRelayNamespace.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureRelayNamespace.");
    }
    ResourceValidationUtils.validateAzureRegion(getRegion());
    ResourceValidationUtils.validateAzureNamespace(getNamespaceName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureRelayNamespaceResource that = (ControlledAzureRelayNamespaceResource) o;

    return namespaceName.equals(that.getNamespaceName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + namespaceName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String namespaceName;

    public ControlledAzureRelayNamespaceResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder namespaceName(String namespaceName) {
      this.namespaceName = namespaceName;
      return this;
    }

    public ControlledAzureRelayNamespaceResource build() {
      return new ControlledAzureRelayNamespaceResource(common, namespaceName);
    }
  }
}
