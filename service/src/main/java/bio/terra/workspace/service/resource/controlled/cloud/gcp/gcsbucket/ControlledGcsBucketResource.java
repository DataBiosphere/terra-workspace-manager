package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

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
      @JsonProperty("bucketName") String bucketName) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState);
    this.bucketName = bucketName;
    validate();
  }

  // Constructor for the builder
  private ControlledGcsBucketResource(ControlledResourceFields common, String bucketName) {
    super(common);
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

  /** {@inheritDoc} */
  @Override
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
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteGcsBucketStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  private static String generateBucketName() {
    return String.format("terra-%s-bucket", UUID.randomUUID());
  }

  public String getBucketName() {
    return bucketName;
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
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_GCS_BUCKET;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GCS_BUCKET;
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
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gcpGcsBucket(toApiResource());
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
    ResourceValidationUtils.validateControlledBucketName(getBucketName());
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

    public ControlledGcsBucketResource.Builder bucketName(@Nullable String bucketName) {
      if (StringUtils.isEmpty(bucketName)) {
        this.bucketName =
            ControlledGcsBucketHandler.getHandler()
                .generateCloudName(common.getWorkspaceId(), common.getName());
      } else {
        this.bucketName = bucketName;
      }
      return this;
    }

    public ControlledGcsBucketResource build() {
      return new ControlledGcsBucketResource(common, bucketName);
    }
  }
}
