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
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

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
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") String applicationId,
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region) {
    super(
        ControlledResourceFields.builder()
            .workspaceUuid(workspaceId)
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .cloningInstructions(cloningInstructions)
            .assignedUser(assignedUser)
            .accessScope(accessScope)
            .managedBy(managedBy)
            .applicationId(applicationId)
            .privateResourceState(privateResourceState)
            .resourceLineage(resourceLineage)
            .properties(properties)
            .createdByEmail(createdByEmail)
            .createdDate(createdDate)
            .lastUpdatedByEmail(lastUpdatedByEmail)
            .lastUpdatedDate(lastUpdatedDate)
            .region(region)
            .build());
    this.bucketName = bucketName;
    validate();
  }

  /*
    // TODO: PF-2512 remove constructor above and enable this constructor


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
  */

  // Constructor for the builder
  private ControlledGcsBucketResource(ControlledResourceFields common, String bucketName) {
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

  // -- getters for backward compatibility --
  // TODO: PF-2512 Remove these getters
  public UUID getWorkspaceId() {
    return super.getWorkspaceId();
  }

  public UUID getResourceId() {
    return super.getResourceId();
  }

  public String getName() {
    return super.getName();
  }

  public String getDescription() {
    return super.getDescription();
  }

  public CloningInstructions getCloningInstructions() {
    return super.getCloningInstructions();
  }

  public Optional<String> getAssignedUser() {
    return super.getAssignedUser();
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return super.getPrivateResourceState();
  }

  public AccessScopeType getAccessScope() {
    return super.getAccessScope();
  }

  public ManagedByType getManagedBy() {
    return super.getManagedBy();
  }

  public String getApplicationId() {
    return super.getApplicationId();
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return super.getResourceLineage();
  }

  public ImmutableMap<String, String> getProperties() {
    return super.getProperties();
  }

  public String getCreatedByEmail() {
    return super.getCreatedByEmail();
  }

  public OffsetDateTime getCreatedDate() {
    return super.getCreatedDate();
  }

  public String getLastUpdatedByEmail() {
    return super.getLastUpdatedByEmail();
  }

  public OffsetDateTime getLastUpdatedDate() {
    return super.getLastUpdatedDate();
  }

  public String getRegion() {
    return super.getRegion();
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

  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    ControlledGcsBucketResource resource =
        getResourceFromFlightInputParameters(flight, WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    RetryRule gcpRetry = RetryRules.cloud();

    // Retrieve existing attributes in case of undo later.
    flight.addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService(),
            RetrieveGcsBucketCloudAttributesStep.RetrievalMode.UPDATE_PARAMETERS),
        gcpRetry);

    // Update the bucket's cloud attributes
    flight.addStep(
        new UpdateGcsBucketStep(
            resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetry);
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
