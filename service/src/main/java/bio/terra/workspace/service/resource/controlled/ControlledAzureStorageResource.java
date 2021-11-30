package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiAzureStorageAttributes;
import bio.terra.workspace.generated.model.ApiAzureStorageResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureStorageResource extends ControlledResource {
  private final String storageAccountName;
  private final String region;

  @JsonCreator
  public ControlledAzureStorageResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("storageAccountName") String storageAccountName,
      @JsonProperty("region") String region) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy);
    this.storageAccountName = storageAccountName;
    this.region = region;
    validate();
  }

  public ControlledAzureStorageResource(DbResource dbResource) {
    super(dbResource);
    ControlledAzureStorageAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureStorageAttributes.class);
    this.storageAccountName = attributes.getStorageAccountName();
    this.region = attributes.getRegion();
    validate();
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  public String getRegion() {
    return region;
  }

  public ApiAzureStorageAttributes toApiAttributes() {
    return new ApiAzureStorageAttributes()
        .storageAccountName(getStorageAccountName())
        .region(region.toString());
  }

  public ApiAzureStorageResource toApiResource() {
    return new ApiAzureStorageResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.AZURE_STORAGE_ACCOUNT;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureStorageAttributes(getStorageAccountName(), getRegion()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.AZURE_STORAGE_ACCOUNT) {
      throw new InconsistentFieldsException("Expected AZURE_STORAGE_ACCOUNT");
    }
    if (getStorageAccountName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required storage account name field for ControlledAzureStorage.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureStorage.");
    }
    ValidationUtils.validateStorageAccountName(getStorageAccountName());
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

  public static ControlledAzureStorageResource.Builder builder() {
    return new ControlledAzureStorageResource.Builder();
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
    private String storageAccountName;
    private String region;

    public ControlledAzureStorageResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledAzureStorageResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledAzureStorageResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledAzureStorageResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledAzureStorageResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ControlledAzureStorageResource.Builder storageAccountName(String storageAccountName) {
      this.storageAccountName = storageAccountName;
      return this;
    }

    public ControlledAzureStorageResource.Builder region(String region) {
      this.region = region;
      return this;
    }

    public ControlledAzureStorageResource.Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public ControlledAzureStorageResource.Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public ControlledAzureStorageResource.Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public ControlledAzureStorageResource build() {
      return new ControlledAzureStorageResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          accessScope,
          managedBy,
          storageAccountName,
          region);
    }
  }
}
