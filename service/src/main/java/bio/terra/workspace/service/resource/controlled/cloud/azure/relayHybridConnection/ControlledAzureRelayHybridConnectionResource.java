package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureRelayHybridConnectionAttributes;
import bio.terra.workspace.generated.model.ApiAzureRelayHybridConnectionResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import java.util.UUID;

public class ControlledAzureRelayHybridConnectionResource extends ControlledResource {
  private final String namespaceName;
  private final String hybridConnectionName;
  private final Boolean requiresClientAuthorization;

  @JsonCreator
  public ControlledAzureRelayHybridConnectionResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") UUID applicationId,
      @JsonProperty("namespaceName") String namespaceName,
      @JsonProperty("hybridConnectionName") String hybridConnectionName,
      @JsonProperty("requiresClientAuthorization") Boolean requiresClientAuthorization) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState);
    this.namespaceName = namespaceName;
    this.hybridConnectionName = hybridConnectionName;
    this.requiresClientAuthorization = requiresClientAuthorization;
    validate();
  }

  public ControlledAzureRelayHybridConnectionResource(
      ControlledResourceFields common, String namespaceName, String hybridConnectionName, Boolean requiresClientAuthorization) {
    super(common);
    this.namespaceName = namespaceName;
    this.hybridConnectionName = hybridConnectionName;
    this.requiresClientAuthorization = requiresClientAuthorization;
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

  /** {@inheritDoc} */
  @Override
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
        new GetAzureRelayHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureRelayHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureRelayHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public String getHybridConnectionName() {
    return hybridConnectionName;
  }

  public Boolean isRequiresClientAuthorization() {
    return requiresClientAuthorization;
  }

  public ApiAzureRelayHybridConnectionAttributes toApiAttributes() {
    return new ApiAzureRelayHybridConnectionAttributes()
        .namespaceName(getNamespaceName())
        .hybridConnectionName(getHybridConnectionName())
        .requiresClientAuthorization(isRequiresClientAuthorization());
  }

  public ApiAzureRelayHybridConnectionResource toApiResource() {
    return new ApiAzureRelayHybridConnectionResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_RELAY_HYBRID_CONNECTION;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_RELAY_HYBRID_CONNECTION;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureRelayHybridConnectionAttributes(getNamespaceName(), getHybridConnectionName(), isRequiresClientAuthorization()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureRelayHybridConnection(toApiAttributes());
    return union;
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.azureRelayHybridConnection(toApiResource());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_RELAY_HYBRID_CONNECTION
        || getResourceFamily() != WsmResourceFamily.AZURE_RELAY_HYBRID_CONNECTION
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_RELAY_HYBRID_CONNECTION");
    }
    if (getName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required Name field for ControlledAzureRelayHybridConnection.");
    }
    if (getHybridConnectionName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required hybridConnectionName field for ControlledAzureRelayHybridConnection.");
    }
    if (isRequiresClientAuthorization() == null) {
      throw new MissingRequiredFieldException(
          "Missing required requiresClientAuthorization field for ControlledAzureRelayHybridConnection.");
    }
    ResourceValidationUtils.validateAzureNamespace(getNamespaceName());
    ResourceValidationUtils.validateAzureHybridConnectionName(getHybridConnectionName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureRelayHybridConnectionResource that = (ControlledAzureRelayHybridConnectionResource) o;

    return namespaceName.equals(that.getNamespaceName()) && hybridConnectionName.equals(that.getHybridConnectionName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + namespaceName.hashCode() + hybridConnectionName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String namespaceName;
    private String hybridConnectionName;
    private Boolean requiresClientAuthorization;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder namespaceName(String namespaceName) {
      this.namespaceName = namespaceName;
      return this;
    }

    public Builder hybridConnectionName(String hybridConnectionName) {
      this.hybridConnectionName = hybridConnectionName;
      return this;
    }

    public Builder requiresClientAuthorization(Boolean requiresClientAuthorization) {
      this.requiresClientAuthorization = requiresClientAuthorization;
      return this;
    }

    public ControlledAzureRelayHybridConnectionResource build() {
      return new ControlledAzureRelayHybridConnectionResource(common, namespaceName, hybridConnectionName, requiresClientAuthorization);
    }
  }
}
