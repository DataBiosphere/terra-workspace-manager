package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;

public class ControlledGcsBucketResource extends ControlledResource {
  private final ControlledGcsBucketAttributes attributes;

  @JsonCreator
  public ControlledGcsBucketResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("controlledAccessType") ControlledAccessType controlledAccessType,
      @JsonProperty("bucketName") String bucketName) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        controlledAccessType);
    this.attributes = new ControlledGcsBucketAttributes(bucketName);
    validate();
  }

  public ControlledGcsBucketResource(DbResource dbResource) {
    super(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName().orElse(null),
        dbResource.getDescription().orElse(null),
        dbResource.getCloningInstructions(),
        dbResource.getAssignedUser().orElse(null),
        dbResource.getAccessType().orElse(null));
    this.attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledGcsBucketAttributes.class);
    validate();
  }

  public ControlledGcsBucketAttributes getAttributes() {
    return attributes;
  }

  public GoogleBucketStoredAttributes toApiModel() {
    return new GoogleBucketStoredAttributes().bucketName(attributes.getBucketName());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GCS_BUCKET;
  }

  @Override
  public String getJsonAttributes() {
    return DbSerDes.toJson(attributes);
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GCS_BUCKET) {
      throw new InconsistentFieldsException("Expected GCS_BUCKET");
    }
    if (getAttributes() == null || getAttributes().getBucketName() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledGcsBucket.");
    }
    ValidationUtils.validateBucketName(getAttributes().getBucketName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ControlledGcsBucketResource)) {
      return false;
    }
    return super.equals(o); // no fields in this class
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode());
  }
}
