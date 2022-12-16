package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceApiFields;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedGcsObjectResource extends ReferencedResource {
  private final String bucketName;
  private final String objectName;

  /**
   * Constructor for serialized form for Stairway use and used by the builder
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - name of the referenced resource.
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param bucketName bucket name
   * @param objectName name for the file in the bucket
   * @param resourceLineage resource lineage
   */
  @JsonCreator
  public ReferencedGcsObjectResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("objectName") String objectName,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        resourceLineage,
        properties,
        createdByEmail,
        createdDate);
    this.bucketName = bucketName;
    this.objectName = objectName;
    validate();
  }

  private ReferencedGcsObjectResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.bucketName = builder.bucketName;
    this.objectName = builder.objectName;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedGcsObjectResource(DbResource dbResource) {
    super(dbResource);
    ReferencedGcsObjectAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedGcsObjectAttributes.class);
    this.bucketName = attributes.getBucketName();
    this.objectName = attributes.getObjectName();
    validate();
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getObjectName() {
    return objectName;
  }

  public ApiGcpGcsObjectAttributes toApiAttributes() {
    return new ApiGcpGcsObjectAttributes().bucketName(getBucketName()).fileName(getObjectName());
  }

  public ApiGcpGcsObjectResource toApiResource(WsmResourceApiFields apiFields) {
    return new ApiGcpGcsObjectResource()
        .metadata(super.toApiMetadata(apiFields))
        .attributes(toApiAttributes());
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

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_GCP_GCS_OBJECT;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GCS_OBJECT;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGcsObjectAttributes(bucketName, objectName));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpGcsObject(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion(WsmResourceApiFields apiFields) {
    return new ApiResourceUnion().gcpGcsObject(toApiResource(apiFields));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.REFERENCED_GCP_GCS_OBJECT) {
      throw new InconsistentFieldsException("Expected GCS_OBJECT");
    }
    if (Strings.isNullOrEmpty(getBucketName()) || Strings.isNullOrEmpty(getObjectName())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceGcsObjectResource.");
    }
    ResourceValidationUtils.validateReferencedBucketName(getBucketName());
    ResourceValidationUtils.validateGcsObjectName(getObjectName());
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
    return crlService.canReadGcsObject(bucketName, objectName, maybePetCreds.orElse(userRequest));
  }

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    ReferencedGcsObjectResource.Builder resultBuilder =
        toBuilder()
            .wsmResourceFields(
                buildReferencedCloneResourceCommonFields(
                    destinationWorkspaceUuid,
                    destinationResourceId,
                    destinationFolderId,
                    name,
                    description,
                    createdByEmail));
    return resultBuilder.build();
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
        .objectName(getObjectName())
        .wsmResourceFields(getWsmResourceFields());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private WsmResourceFields wsmResourceFields;
    private String bucketName;
    private String objectName;

    public Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder objectName(String objectName) {
      this.objectName = objectName;
      return this;
    }

    public ReferencedGcsObjectResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGcsObjectResource(this);
    }
  }
}
