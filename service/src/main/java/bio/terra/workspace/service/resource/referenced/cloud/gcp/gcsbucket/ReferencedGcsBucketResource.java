package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jetbrains.annotations.Nullable;

public class ReferencedGcsBucketResource extends ReferencedResource {
  private final String bucketName;

  /**
   * Constructor for serialized form for Stairway use and used by the builder
   *
   * @param resourceFields common resource fields
   * @param bucketName bucket name
   */
  @JsonCreator
  public ReferencedGcsBucketResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("bucketName") String bucketName) {
    super(resourceFields);
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

  private ReferencedGcsBucketResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.bucketName = builder.bucketName;
    validate();
  }

  // -- getters used in serialization --
  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public String getBucketName() {
    return bucketName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_GCP_GCS_BUCKET;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GCS_BUCKET;
  }

  public ApiGcpGcsBucketAttributes toApiAttributes() {
    return new ApiGcpGcsBucketAttributes().bucketName(getBucketName());
  }

  public ApiGcpGcsBucketResource toApiResource() {
    return new ApiGcpGcsBucketResource()
        .metadata(super.toApiMetadata())
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
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGcsBucketAttributes(bucketName));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpGcsBucket(toApiAttributes());
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.REFERENCED_GCP_GCS_BUCKET) {
      throw new InconsistentFieldsException("Expected referenced GCP GCS_BUCKET");
    }
    if (Strings.isNullOrEmpty(getBucketName())) {
      throw new MissingRequiredFieldException("Missing required field for ReferenceGcsBucket.");
    }
    // Validate in case there's a typo and user gave wrong name. This gives a slightly more usable
    // error message than "You do not have access to bucket".
    ResourceValidationUtils.validateBucketNameAllowsUnderscore(getBucketName());
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

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    ReferencedGcsBucketResource.Builder resultBuilder =
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

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ReferencedGcsBucketResource that = (ReferencedGcsBucketResource) o;

    return new EqualsBuilder()
        .appendSuper(super.partialEqual(o))
        .append(bucketName, that.getBucketName())
        .isEquals();
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public Builder toBuilder() {
    return builder().bucketName(getBucketName()).wsmResourceFields(getWsmResourceFields());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String bucketName;
    private WsmResourceFields wsmResourceFields;

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public ReferencedGcsBucketResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGcsBucketResource(this);
    }
  }
}
