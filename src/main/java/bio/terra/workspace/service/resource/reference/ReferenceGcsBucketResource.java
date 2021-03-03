package bio.terra.workspace.service.resource.reference;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.GoogleBucketReference;
import bio.terra.workspace.generated.model.GoogleBucketUid;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ReferenceGcsBucketResource extends ReferenceResource {
  private final ReferenceGcsBucketAttributes attributes;

  /**
   * Constructor for serialized form for Stairway use
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param bucketName bucket name
   */
  @JsonCreator
  public ReferenceGcsBucketResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("bucketName") String bucketName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.attributes = new ReferenceGcsBucketAttributes(bucketName);
    validate();
  }

  /**
   * Constructor from database metadata
   * @param dbResource database form of resources
   */
  public ReferenceGcsBucketResource(DbResource dbResource) {
    super(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName().orElse(null),
        dbResource.getDescription().orElse(null),
        dbResource.getCloningInstructions());
    this.attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferenceGcsBucketAttributes.class);
    validate();
  }

  public ReferenceGcsBucketAttributes getAttributes() {
    return attributes;
  }

  public GoogleBucketReference toApiModel() {
    return new GoogleBucketReference()
            .metadata(super.toApiMetadata())
            .bucket(new GoogleBucketUid().bucketName(getAttributes().getBucketName()));
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
      throw new MissingRequiredFieldException("Missing required field for ReferenceGcsBucket.");
    }
    ValidationUtils.validateBucketName(getAttributes().getBucketName());
  }
}
