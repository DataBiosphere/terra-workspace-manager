package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class ControlledGcsBucketResource extends ControlledResource {

  private final String bucketName;

  @JsonCreator
  public ControlledGcsBucketResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("bucketName") String bucketName) {
    super(resourceFields, controlledResourceFields);
    this.bucketName = bucketName;
    validate();
  }

  // Constructor for the builder
  @VisibleForTesting
  public ControlledGcsBucketResource(ControlledResourceFields common, String bucketName) {
    super(common);
    this.bucketName = bucketName;
    validateForNewBucket();
  }

  public ControlledGcsBucketResource(DbResource dbResource, String bucketName) {
    super(dbResource);
    this.bucketName = bucketName;
    validate();
  }

  public static ControlledGcsBucketResource.Builder builder() {
    return new ControlledGcsBucketResource.Builder();
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

  // -- getters used in serialization --
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getBucketName() {
    return bucketName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_GCS_BUCKET;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GCS_BUCKET;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.GLOBAL)
            .addParameter("bucketName", getBucketName()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();
    flight.addStep(
        new CreateGcsBucketStep(
            flightBeanBag.getCrlService(), this, flightBeanBag.getGcpCloudContextService()),
        cloudRetry);
    flight.addStep(
        new GcsBucketCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteGcsBucketStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  public ApiGcpGcsBucketAttributes toApiAttributes() {
    return new ApiGcpGcsBucketAttributes().bucketName(getBucketName());
  }

  public ApiGcpGcsBucketResource toApiResource() {
    return new ApiGcpGcsBucketResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    WsmResourceFields wsmResourceFields =
        buildReferencedCloneResourceCommonFields(
            destinationWorkspaceUuid,
            destinationResourceId,
            destinationFolderId,
            name,
            description,
            createdByEmail);

    ReferencedGcsBucketResource.Builder resultBuilder =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(wsmResourceFields)
            .bucketName(getBucketName());
    return resultBuilder.build();
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledGcsBucketAttributes(getBucketName()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpGcsBucket(toApiAttributes());
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_GCS_BUCKET
        || getResourceFamily() != WsmResourceFamily.GCS_BUCKET
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP GCS_BUCKET");
    }
    if (getBucketName() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledGcsBucket.");
    }
    // Allow underscore bucket name to be backward compatible. The database contains bucket with
    // underscore bucketname.
    ResourceValidationUtils.validateBucketNameAllowsUnderscore(bucketName);
  }

  public void validateForNewBucket() {
    validate();
    // Disallow underscore in new terra managed GCS bucket.
    ResourceValidationUtils.validateBucketNameDisallowUnderscore(bucketName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

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
    private ControlledResourceFields common;
    private String bucketName;

    public ControlledGcsBucketResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledGcsBucketResource.Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public ControlledGcsBucketResource build() {
      return new ControlledGcsBucketResource(common, bucketName);
    }
  }
}
