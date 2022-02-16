package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceResource;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceAttributes;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureRelayNamespaceResource extends ControlledResource {
  private final String namespaceName;
  private final String region;

  @JsonCreator
  public ControlledAzureRelayNamespaceResource(
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
      @JsonProperty("region") String region) {

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
    this.region = region;
    validate();
  }

  public ControlledAzureRelayNamespaceResource(DbResource dbResource) {
    super(dbResource);
    ControlledAzureRelayNamespaceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureRelayNamespaceAttributes.class);
    this.namespaceName = attributes.getNamespaceName();
    this.region = attributes.getRegion();
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
        new GetAzureRelayNamespaceStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureRelayNamespaceStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureRelayNamespaceStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public String getRegion() {
    return region;
  }

  public ApiAzureRelayNamespaceAttributes toApiAttributes() {
    return new ApiAzureRelayNamespaceAttributes().namespaceName(getName()).region(region.toString());
  }

  public ApiAzureRelayNamespaceResource toApiResource() {
    return new ApiAzureRelayNamespaceResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_RELAY_NAMESPACE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledAzureRelayNamespaceAttributes(getNamespaceName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureRelayNamespace(toApiAttributes());
    return union;
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.azureRelayNamespace(toApiResource());
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
    ValidationUtils.validateRegion(getRegion());
    ValidationUtils.validateAzureNamespace(getNamespaceName());
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
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private String assignedUser;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private UUID applicationId;
    private String namespaceName;
    private String region;

    public Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder namespaceName(String namespaceName) {
      this.namespaceName = namespaceName;
      return this;
    }

    public Builder region(String region) {
      this.region = region;
      return this;
    }

    public Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public Builder privateResourceState(
        PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    private PrivateResourceState defaultPrivateResourceState() {
      return this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
          ? PrivateResourceState.INITIALIZING
          : PrivateResourceState.NOT_APPLICABLE;
    }

    public Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public Builder applicationId(UUID applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public ControlledAzureRelayNamespaceResource build() {
      return new ControlledAzureRelayNamespaceResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          Optional.ofNullable(privateResourceState).orElse(defaultPrivateResourceState()),
          accessScope,
          managedBy,
          applicationId,
          namespaceName,
          region);
    }
  }
}
