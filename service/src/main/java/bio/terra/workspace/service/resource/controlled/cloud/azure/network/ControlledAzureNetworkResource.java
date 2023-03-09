package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureNetworkAttributes;
import bio.terra.workspace.generated.model.ApiAzureNetworkResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureNetworkResource extends ControlledResource {
  private final String networkName;
  private final String subnetName;
  private final String addressSpaceCidr;
  private final String subnetAddressCidr;

  @JsonCreator
  public ControlledAzureNetworkResource(
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
      @JsonProperty("networkName") String networkName,
      @JsonProperty("subnetName") String subnetName,
      @JsonProperty("addressSpaceCidr") String addressSpaceCidr,
      @JsonProperty("subnetAddressCidr") String subnetAddressCidr,
      @JsonProperty("region") String region,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate) {
    super(
        ControlledResourceFields.builder()
            .workspaceUuid(workspaceId)
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .cloningInstructions(cloningInstructions)
            .assignedUser(assignedUser)
            .accessScope(accessScope)
            .managedBy(managedBy)
            .applicationId(applicationId)
            .privateResourceState(privateResourceState)
            .resourceLineage(resourceLineage)
            .properties(properties)
            .createdByEmail(createdByEmail)
            .createdDate(createdDate)
            .lastUpdatedByEmail(lastUpdatedByEmail)
            .lastUpdatedDate(lastUpdatedDate)
            .region(region)
            .build());
    this.networkName = networkName;
    this.subnetName = subnetName;
    this.addressSpaceCidr = addressSpaceCidr;
    this.subnetAddressCidr = subnetAddressCidr;
    validate();
  }

  /*
   // TODO: PF-2512 remove constructor above and enable this constructor
   @JsonCreator
   public ControlledAzureNetworkResource(
       @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
       @JsonProperty("wsmControlledResourceFields")
           WsmControlledResourceFields controlledResourceFields,
       @JsonProperty("networkName") String networkName,
       @JsonProperty("subnetName") String subnetName,
       @JsonProperty("addressSpaceCidr") String addressSpaceCidr,
       @JsonProperty("subnetAddressCidr") String subnetAddressCidr) {
     super(resourceFields, controlledResourceFields);
     this.networkName = networkName;
     this.subnetName = subnetName;
     this.addressSpaceCidr = addressSpaceCidr;
     this.subnetAddressCidr = subnetAddressCidr;
     validate();
   }
  */

  // Constructor for the builder
  private ControlledAzureNetworkResource(
      ControlledResourceFields common,
      String networkName,
      String subnetName,
      String addressSpaceCidr,
      String subnetAddressCidr) {
    super(common);
    this.networkName = networkName;
    this.subnetName = subnetName;
    this.addressSpaceCidr = addressSpaceCidr;
    this.subnetAddressCidr = subnetAddressCidr;
    validate();
  }

  public static ControlledAzureNetworkResource.Builder builder() {
    return new ControlledAzureNetworkResource.Builder();
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

  public String getNetworkName() {
    return networkName;
  }

  public String getSubnetName() {
    return subnetName;
  }

  public String getAddressSpaceCidr() {
    return addressSpaceCidr;
  }

  public String getSubnetAddressCidr() {
    return subnetAddressCidr;
  }

  // -- getters for backward compatibility --
  // TODO: PF-2512 Remove these getters
  public UUID getWorkspaceId() {
    return super.getWorkspaceId();
  }

  public UUID getResourceId() {
    return super.getResourceId();
  }

  public String getName() {
    return super.getName();
  }

  public String getDescription() {
    return super.getDescription();
  }

  public CloningInstructions getCloningInstructions() {
    return super.getCloningInstructions();
  }

  public Optional<String> getAssignedUser() {
    return super.getAssignedUser();
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return super.getPrivateResourceState();
  }

  public AccessScopeType getAccessScope() {
    return super.getAccessScope();
  }

  public ManagedByType getManagedBy() {
    return super.getManagedBy();
  }

  public String getApplicationId() {
    return super.getApplicationId();
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return super.getResourceLineage();
  }

  public ImmutableMap<String, String> getProperties() {
    return super.getProperties();
  }

  public String getCreatedByEmail() {
    return super.getCreatedByEmail();
  }

  public OffsetDateTime getCreatedDate() {
    return super.getCreatedDate();
  }

  public String getLastUpdatedByEmail() {
    return super.getLastUpdatedByEmail();
  }

  public OffsetDateTime getLastUpdatedDate() {
    return super.getLastUpdatedDate();
  }

  public String getRegion() {
    return super.getRegion();
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_NETWORK;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_NETWORK;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("networkName", getNetworkName()));
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
        new GetAzureNetworkStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureNetworkStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureNetworkStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureNetworkAttributes toApiAttributes() {
    return new ApiAzureNetworkAttributes()
        .networkName(getNetworkName())
        .subnetName(getSubnetName())
        .addressSpaceCidr(getAddressSpaceCidr())
        .subnetAddressCidr(getSubnetAddressCidr())
        .region(getRegion());
  }

  public ApiAzureNetworkResource toApiResource() {
    return new ApiAzureNetworkResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureNetworkAttributes(
            getNetworkName(),
            getSubnetName(),
            getAddressSpaceCidr(),
            getSubnetAddressCidr(),
            getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureNetwork(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_NETWORK
        || getResourceFamily() != WsmResourceFamily.AZURE_NETWORK
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AZURE_NETWORK");
    }
    if (getNetworkName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required networkName field for ControlledAzureNetwork.");
    }
    if (getSubnetName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required subnetName field for ControlledAzureNetwork.");
    }
    if (getAddressSpaceCidr() == null) {
      throw new MissingRequiredFieldException(
          "Missing required addressSpaceCidr field for ControlledAzureNetwork.");
    }
    if (getSubnetAddressCidr() == null) {
      throw new MissingRequiredFieldException(
          "Missing required subnetAddressCidr field for ControlledAzureNetwork.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureNetwork.");
    }
    ResourceValidationUtils.validateAzureRegion(getRegion());
    ResourceValidationUtils.validateAzureNetworkName(getNetworkName());
    ResourceValidationUtils.validateAzureIPorSubnetName(getSubnetName());
    ResourceValidationUtils.validateAzureCidrBlock(getAddressSpaceCidr());
    ResourceValidationUtils.validateAzureCidrBlock(getSubnetAddressCidr());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureNetworkResource that = (ControlledAzureNetworkResource) o;

    return networkName.equals(that.getNetworkName());
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAzureNetworkResource that = (ControlledAzureNetworkResource) o;

    return networkName.equals(that.getNetworkName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + networkName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String networkName;
    private String subnetName;
    private String addressSpaceCidr;
    private String subnetAddressCidr;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureNetworkResource.Builder networkName(String networkName) {
      this.networkName = networkName;
      return this;
    }

    public ControlledAzureNetworkResource.Builder subnetName(String subnetName) {
      this.subnetName = subnetName;
      return this;
    }

    public ControlledAzureNetworkResource.Builder addressSpaceCidr(String addressSpaceCidr) {
      this.addressSpaceCidr = addressSpaceCidr;
      return this;
    }

    public ControlledAzureNetworkResource.Builder subnetAddressCidr(String subnetAddressCidr) {
      this.subnetAddressCidr = subnetAddressCidr;
      return this;
    }

    public ControlledAzureNetworkResource build() {
      return new ControlledAzureNetworkResource(
          common, networkName, subnetName, addressSpaceCidr, subnetAddressCidr);
    }
  }
}
