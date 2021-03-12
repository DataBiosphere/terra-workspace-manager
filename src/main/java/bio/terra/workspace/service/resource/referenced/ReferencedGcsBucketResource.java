package bio.terra.workspace.service.resource.referenced;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcsBucketReference;
import bio.terra.workspace.generated.model.ApiGoogleBucketUid;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;

public class ReferencedGcsBucketResource extends ReferencedResource {
  private final String bucketName;

  /**
   * Constructor for serialized form for Stairway use and used by the builder
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param bucketName bucket name
   */
  @JsonCreator
  public ReferencedGcsBucketResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("bucketName") String bucketName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.bucketName = bucketName;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedGcsBucketResource(DbResource dbResource) {
    super(dbResource);
    ReferencedGcsBucketAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedGcsBucketAttributes.class);
    this.bucketName = attributes.getBucketName();
    validate();
  }

  public String getBucketName() {
    return bucketName;
  }

  public ApiGcsBucketReference toApiModel() {
    return new ApiGcsBucketReference()
        .metadata(super.toApiMetadata())
        .bucket(new ApiGoogleBucketUid().bucketName(getBucketName()));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GCS_BUCKET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGcsBucketAttributes(bucketName));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GCS_BUCKET) {
      throw new InconsistentFieldsException("Expected GCS_BUCKET");
    }
    if (Strings.isNullOrEmpty(getBucketName())) {
      throw new MissingRequiredFieldException("Missing required field for ReferenceGcsBucket.");
    }
    ValidationUtils.validateBucketName(getBucketName());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String bucketName;

    public Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder cloningInstructions(CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public ReferencedGcsBucketResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGcsBucketResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          bucketName);
    }
  }
}
