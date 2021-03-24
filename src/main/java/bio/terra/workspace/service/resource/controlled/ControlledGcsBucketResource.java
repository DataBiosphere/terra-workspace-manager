package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcsBucketAttributes;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;

public class ControlledGcsBucketResource extends ControlledResource {
  private final String bucketName;

  @JsonCreator
  public ControlledGcsBucketResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("bucketName") String bucketName) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy);
    this.bucketName = bucketName;
    validate();
  }

  public ControlledGcsBucketResource(DbResource dbResource) {
    super(dbResource);
    ControlledGcsBucketAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledGcsBucketAttributes.class);
    this.bucketName = attributes.getBucketName();
    validate();
  }

  public static ControlledGcsBucketResource.Builder builder() {
    return new ControlledGcsBucketResource.Builder();
  }

  public String getBucketName() {
    return bucketName;
  }

  public ApiGcsBucketAttributes toApiModel() {
    return new ApiGcsBucketAttributes().bucketName(getBucketName());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GCS_BUCKET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledGcsBucketAttributes(getBucketName()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GCS_BUCKET) {
      throw new InconsistentFieldsException("Expected GCS_BUCKET");
    }
    if (getBucketName() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledGcsBucket.");
    }
    ValidationUtils.validateBucketName(getBucketName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledGcsBucketResource that = (ControlledGcsBucketResource) o;

    return bucketName.equals(that.bucketName);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + bucketName.hashCode();
    return result;
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
    private String bucketName;

    public ControlledGcsBucketResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledGcsBucketResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledGcsBucketResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledGcsBucketResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledGcsBucketResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ControlledGcsBucketResource.Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public ControlledGcsBucketResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ControlledGcsBucketResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          assignedUser,
          accessScope,
          managedBy,
          bucketName);
    }
  }
}
