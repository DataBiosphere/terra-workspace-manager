package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiAzureNetworkAttributes;
import bio.terra.workspace.generated.model.ApiAzureNetworkResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureNetworkResource extends ControlledResource {
  private final String networkName;
  private final String subnetName;
  private final String addressSpaceCidr;
  private final String subnetAddressCidr;
  private final String region;

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
      @JsonProperty("applicationId") UUID applicationId,
      @JsonProperty("networkName") String networkName,
      @JsonProperty("subnetName") String subnetName,
      @JsonProperty("addressSpaceCidr") String addressSpaceCidr,
      @JsonProperty("subnetAddressCidr") String subnetAddressCidr,
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
    this.networkName = networkName;
    this.subnetName = subnetName;
    this.addressSpaceCidr = addressSpaceCidr;
    this.subnetAddressCidr = subnetAddressCidr;
    this.region = region;
    validate();
  }

  public ControlledAzureNetworkResource(DbResource dbResource) {
    super(dbResource);
    ControlledAzureNetworkAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureNetworkAttributes.class);
    this.networkName = attributes.getNetworkName();
    this.subnetName = attributes.getSubnetName();
    this.addressSpaceCidr = attributes.getAddressSpaceCidr();
    this.subnetAddressCidr = attributes.getSubnetAddressCidr();
    this.region = attributes.getRegion();
    validate();
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

  public String getRegion() {
    return region;
  }

  public ApiAzureNetworkAttributes toApiAttributes() {
    return new ApiAzureNetworkAttributes()
        .networkName(getNetworkName())
        .subnetName(getSubnetName())
        .addressSpaceCidr(getAddressSpaceCidr())
        .subnetAddressCidr(getSubnetAddressCidr())
        .region(region.toString());
  }

  public ApiAzureNetworkResource toApiResource() {
    return new ApiAzureNetworkResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.AZURE_NETWORK;
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
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.AZURE_NETWORK) {
      throw new InconsistentFieldsException("Expected AZURE_NETWORK");
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
    ValidationUtils.validateRegion(getRegion());
    ValidationUtils.validateAzureNetworkName(getNetworkName());
    ValidationUtils.validateAzureIPorSubnetName(getSubnetName());
    ValidationUtils.validateAzureCidrBlock(getAddressSpaceCidr());
    ValidationUtils.validateAzureCidrBlock(getSubnetAddressCidr());
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
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + networkName.hashCode();
    return result;
  }

  public static ControlledAzureNetworkResource.Builder builder() {
    return new ControlledAzureNetworkResource.Builder();
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String assignedUser;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private UUID applicationId;
    private String networkName;
    private String subnetName;
    private String addressSpaceCidr;
    private String subnetAddressCidr;
    private String region;

    public ControlledAzureNetworkResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledAzureNetworkResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledAzureNetworkResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledAzureNetworkResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledAzureNetworkResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
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

    public ControlledAzureNetworkResource.Builder region(String region) {
      this.region = region;
      return this;
    }

    public ControlledAzureNetworkResource.Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public ControlledAzureNetworkResource.Builder privateResourceState(
        PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    private PrivateResourceState defaultPrivateResourceState() {
      return this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
          ? PrivateResourceState.INITIALIZING
          : PrivateResourceState.NOT_APPLICABLE;
    }

    public ControlledAzureNetworkResource.Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public ControlledAzureNetworkResource.Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public ControlledAzureNetworkResource.Builder applicationId(UUID applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public ControlledAzureNetworkResource build() {
      return new ControlledAzureNetworkResource(
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
          networkName,
          subnetName,
          addressSpaceCidr,
          subnetAddressCidr,
          region);
    }
  }
}
