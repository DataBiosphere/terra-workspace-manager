package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketFileAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketFileResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;

public class ReferencedGcsBucketFileResource extends ReferencedResource {
  private final String bucketName;
  private final String fileName;

  /**
   * Constructor for serialized form for Stairway use and used by the builder
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param bucketName bucket name
   * @param fileName name for the file in the bucket
   */
  @JsonCreator
  public ReferencedGcsBucketFileResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("fileName") String fileName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.bucketName = bucketName;
    this.fileName = fileName;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedGcsBucketFileResource(DbResource dbResource) {
    super(dbResource);
    ReferencedGcsBucketFileAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedGcsBucketFileAttributes.class);
    this.bucketName = attributes.getBucketName();
    this.fileName = attributes.getFileName();
    validate();
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getFileName() {
    return fileName;
  }

  public ApiGcpGcsBucketFileAttributes toApiAttributes() {
    return new ApiGcpGcsBucketFileAttributes().bucketName(getBucketName()).fileName(getFileName());
  }

  public ApiGcpGcsBucketFileResource toApiModel() {
    return new ApiGcpGcsBucketFileResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GCS_BUCKET_FILE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGcsBucketFileAttributes(bucketName, fileName));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GCS_BUCKET_FILE) {
      throw new InconsistentFieldsException("Expected GCS_BUCKET_FILE");
    }
    if (Strings.isNullOrEmpty(getBucketName()) || Strings.isNullOrEmpty(getFileName())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceGcsBucketFileResource.");
    }
    ValidationUtils.validateBucketName(getBucketName());
    ValidationUtils.validateBucketFileName(getFileName());
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    CrlService crlService = context.getCrlService();
    PetSaService petSaService = context.getPetSaService();
    // If the resource's workspace has a GCP cloud context, use the SA from that context. Otherwise,
    // use the provided credentials. This cannot use arbitrary pet SA credentials, as they may not
    // have the Storage APIs enabled.
    Optional<AuthenticatedUserRequest> maybePetCreds =
        petSaService.getWorkspacePetCredentials(getWorkspaceId(), userRequest);
    return crlService.canReadGcsBucket(bucketName, maybePetCreds.orElse(userRequest));
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public Builder toBuilder() {
    return builder()
        .bucketName(getBucketName())
        .fileName(getFileName())
        .cloningInstructions(getCloningInstructions())
        .description(getDescription())
        .name(getName())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private CloningInstructions cloningInstructions;
    private String bucketName;
    private String fileName;
    private String description;
    private String name;
    private UUID resourceId;
    private UUID workspaceId;

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

    public Builder fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public ReferencedGcsBucketFileResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGcsBucketFileResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          bucketName,
          fileName);
    }
  }
}
