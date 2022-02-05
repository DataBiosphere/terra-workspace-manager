package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckParameters;
import bio.terra.workspace.db.model.UniquenessCheckParameters.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskAttributes;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.resource.ValidationUtils;
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
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureDiskResource extends ControlledResource {
  private final String diskName;
  private final String region;
  private final int size;

  @JsonCreator
  public ControlledAzureDiskResource(
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
      @JsonProperty("diskName") String diskName,
      @JsonProperty("region") String region,
      @JsonProperty("size") int size) {

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
    this.diskName = diskName;
    this.region = region;
    this.size = size;
    validate();
  }

  public ControlledAzureDiskResource(DbResource dbResource) {
    super(dbResource);
    ControlledAzureDiskAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureDiskAttributes.class);
    this.diskName = attributes.getDiskName();
    this.region = attributes.getRegion();
    this.size = attributes.getSize();
    validate();
  }

  /** {@inheritDoc} */
  @Override
  public Optional<UniquenessCheckParameters> getUniquenessCheckParameters() {
    return Optional.of(new UniquenessCheckParameters(
        UniquenessScope.WORKSPACE)
        .addParameter("diskName", getDiskName()));
  }

  public String getDiskName() {
    return diskName;
  }

  public String getRegion() {
    return region;
  }

  public int getSize() {
    return size;
  }

  public ApiAzureDiskResource toApiResource() {
    return new ApiAzureDiskResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  private ApiAzureDiskAttributes toApiAttributes() {
    return new ApiAzureDiskAttributes().diskName(getDiskName()).region(region);
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_DISK;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_DISK;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureDiskAttributes(getDiskName(), getRegion(), getSize()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureDisk(toApiAttributes());
    return union;
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.azureDisk(toApiResource());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_DISK
        || getResourceFamily() != WsmResourceFamily.AZURE_DISK
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_DISK");
    }
    if (getDiskName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required diskName field for ControlledAzureDisk.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureDisk.");
    }
    ValidationUtils.validateRegion(getRegion());
    ValidationUtils.validateAzureDiskName(getDiskName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureDiskResource that = (ControlledAzureDiskResource) o;

    return diskName.equals(that.getDiskName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + diskName.hashCode();
    return result;
  }

  public static ControlledAzureDiskResource.Builder builder() {
    return new ControlledAzureDiskResource.Builder();
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
    private String diskName;
    private String region;
    private int size;

    public ControlledAzureDiskResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledAzureDiskResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledAzureDiskResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledAzureDiskResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledAzureDiskResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ControlledAzureDiskResource.Builder diskName(String diskName) {
      this.diskName = diskName;
      return this;
    }

    public ControlledAzureDiskResource.Builder region(String region) {
      this.region = region;
      return this;
    }

    public ControlledAzureDiskResource.Builder size(int size) {
      this.size = size;
      return this;
    }

    public ControlledAzureDiskResource.Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public ControlledAzureDiskResource.Builder privateResourceState(
        PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    private PrivateResourceState defaultPrivateResourceState() {
      return this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
          ? PrivateResourceState.INITIALIZING
          : PrivateResourceState.NOT_APPLICABLE;
    }

    public ControlledAzureDiskResource.Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public ControlledAzureDiskResource.Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public ControlledAzureDiskResource.Builder applicationId(UUID applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public ControlledAzureDiskResource build() {
      return new ControlledAzureDiskResource(
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
          diskName,
          region,
          size);
    }
  }
}
