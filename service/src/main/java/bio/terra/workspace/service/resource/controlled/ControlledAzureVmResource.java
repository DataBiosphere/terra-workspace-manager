package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiAzureVmAttributes;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureVmResource extends ControlledResource {
  private final String vmName;
  private final String region;
  private final String vmSize;
  private final String vmImageUri;

  private final UUID ipId;
  private final UUID networkId;
  private final UUID diskId;

  @JsonCreator
  public ControlledAzureVmResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("vmName") String vmName,
      @JsonProperty("region") String region,
      @JsonProperty("vmSize") String vmSize,
      @JsonProperty("vmImageUri") String vmImageUri,
      @JsonProperty("ipId") UUID ipId,
      @JsonProperty("networkId") UUID networkId,
      @JsonProperty("diskId") UUID diskId) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy);
    this.vmName = vmName;
    this.region = region;
    this.vmSize = vmSize;
    this.vmImageUri = vmImageUri;
    this.ipId = ipId;
    this.networkId = networkId;
    this.diskId = diskId;
    validate();
  }

  public ControlledAzureVmResource(DbResource dbResource) {
    super(dbResource);
    ControlledAzureVmAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureVmAttributes.class);
    this.vmName = attributes.getVmName();
    this.region = attributes.getRegion();
    this.vmImageUri = attributes.getVmImageUri();
    this.vmSize = attributes.getVmSize();
    this.ipId = attributes.getIpId();
    this.networkId = attributes.getNetworkId();
    this.diskId = attributes.getDiskId();
    validate();
  }

  public String getVmName() {
    return vmName;
  }

  public String getRegion() {
    return region;
  }

  public String getVmSize() {
    return vmSize;
  }

  public String getVmImageUri() {
    return vmImageUri;
  }

  public UUID getIpId() {
    return ipId;
  }

  public UUID getNetworkId() {
    return networkId;
  }

  public UUID getDiskId() {
    return diskId;
  }

  public ApiAzureVmAttributes toApiAttributes() {
    return new ApiAzureVmAttributes()
        .vmName(getVmName())
        .region(getRegion())
        .vmSize(getVmSize())
        .vmImageUri(getVmImageUri())
        .ipId(getIpId())
        .diskId(getDiskId())
        .networkId(getNetworkId());
  }

  public ApiAzureVmResource toApiResource() {
    return new ApiAzureVmResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.AZURE_VM;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureVmAttributes(
            getVmName(),
            getRegion(),
            getVmSize(),
            getVmImageUri(),
            getIpId(),
            getNetworkId(),
            getDiskId()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.AZURE_VM) {
      throw new InconsistentFieldsException("Expected AZURE_VM");
    }
    if (getVmName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required vmName field for ControlledAzureVm.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureVm.");
    }
    if (getVmSize() == null) {
      throw new MissingRequiredFieldException(
          "Missing required valid vmSize field for ControlledAzureVm.");
    }
    if (getIpId() == null) {
      throw new MissingRequiredFieldException("Missing required ipId field for ControlledAzureVm.");
    }
    if (getNetworkId() == null) {
      throw new MissingRequiredFieldException(
          "Missing required networkId field for ControlledAzureVm.");
    }
    if (getDiskId() == null) {
      throw new MissingRequiredFieldException(
          "Missing required diskId field for ControlledAzureVm.");
    }

    ValidationUtils.validateAzureResourceName(getVmName());
    ValidationUtils.validateAzureVmSize(getVmSize());
    ValidationUtils.validateRegion(getRegion());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureVmResource that = (ControlledAzureVmResource) o;

    return vmName.equals(that.getVmName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + vmName.hashCode();
    return result;
  }

  public static ControlledAzureVmResource.Builder builder() {
    return new ControlledAzureVmResource.Builder();
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String assignedUser;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private String vmName;
    private String region;
    private String vmSize;
    private String vmImageUri;
    private UUID ipId;
    private UUID networkId;
    private UUID diskId;

    public ControlledAzureVmResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledAzureVmResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledAzureVmResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledAzureVmResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledAzureVmResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ControlledAzureVmResource.Builder vmName(String vmName) {
      this.vmName = vmName;
      return this;
    }

    public ControlledAzureVmResource.Builder vmSize(String vmSize) {
      this.vmSize = vmSize;
      return this;
    }

    public ControlledAzureVmResource.Builder vmImageUri(String vmImageUri) {
      this.vmImageUri = vmImageUri;
      return this;
    }

    public ControlledAzureVmResource.Builder region(String region) {
      this.region = region;
      return this;
    }

    public ControlledAzureVmResource.Builder ipId(UUID ipId) {
      this.ipId = ipId;
      return this;
    }

    public ControlledAzureVmResource.Builder networkId(UUID networkId) {
      this.networkId = networkId;
      return this;
    }

    public ControlledAzureVmResource.Builder diskId(UUID diskId) {
      this.diskId = diskId;
      return this;
    }

    public ControlledAzureVmResource.Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public ControlledAzureVmResource.Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public ControlledAzureVmResource.Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public ControlledAzureVmResource build() {
      return new ControlledAzureVmResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          accessScope,
          managedBy,
          vmName,
          region,
          vmSize,
          vmImageUri,
          ipId,
          networkId,
          diskId);
    }
  }
}
