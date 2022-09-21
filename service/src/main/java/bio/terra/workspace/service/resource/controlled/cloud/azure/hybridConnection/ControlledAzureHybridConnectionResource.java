package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureHybridConnectionAttributes;
import bio.terra.workspace.generated.model.ApiAzureHybridConnectionResource;
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
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureHybridConnectionResource extends ControlledResource {
  private final String hybridConnectionName;
  private final String namespaceName;

  @JsonCreator
  public ControlledAzureHybridConnectionResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") String applicationId,
      @JsonProperty("hybridConnectionName") String hybridConnectionName,
      @JsonProperty("namespaceName") String namespaceName,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties) {

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
        privateResourceState,
        resourceLineage,
        properties);
    this.hybridConnectionName = hybridConnectionName;
    this.namespaceName = namespaceName;
    validate();
  }

  public ControlledAzureHybridConnectionResource(ControlledResourceFields common, String hybridConnectionName, String namespaceName) {
    super(common);
    this.hybridConnectionName = hybridConnectionName;
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
        new GetAzureHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureHybridConnectionStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public String getHybridConnectionName() {
    return hybridConnectionName;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public ApiAzureHybridConnectionAttributes toApiAttributes() {
    return new ApiAzureHybridConnectionAttributes().namespaceName(namespaceName).hybridConnectionName(hybridConnectionName);
  }

  public ApiAzureHybridConnectionResource toApiResource() {
    return new ApiAzureHybridConnectionResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_HYBRID_CONNECTION;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_HYBRID_CONNECTION;
  }

  @Override
  public String attributesToJson() { // TODO
    return DbSerDes.toJson(new ControlledAzureHybridConnectionAttributes(getHybridConnectionName()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureHybridConnection(toApiAttributes());
    return union;
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.azureHybridConnection(toApiResource());
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
    if (getHybridConnectionName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureRelayNamespace.");
    }
    ResourceValidationUtils.validateAzureHybridConnectionName(getHybridConnectionName());
    ResourceValidationUtils.validateAzureNamespace(namespaceName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureHybridConnectionResource that = (ControlledAzureHybridConnectionResource) o;

    return hybridConnectionName.equals(that.hybridConnectionName); // TODO include namespace
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + hybridConnectionName.hashCode(); // TODO include namespace
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String namespaceName;
    private String region;

    public ControlledAzureHybridConnectionResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder region(String hybridConnectionName, String namespaceName) {
      this. = region; // TODO
      return this;
    }

    public ControlledAzureHybridConnectionResource build() {
      return new ControlledAzureHybridConnectionResource(common, region);
    }
  }
}
