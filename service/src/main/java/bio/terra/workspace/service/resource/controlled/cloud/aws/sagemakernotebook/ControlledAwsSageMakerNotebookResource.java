package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookAttributes;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookResource;
import bio.terra.workspace.generated.model.ApiAwsSagemakerNotebookDefaultBucket;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAwsSageMakerNotebookResource extends ControlledResource {
  private final String instanceId;
  private final String instanceType;
  private final ControlledAwsSageMakerNotebookAttributes.DefaultBucket defaultBucket;

  protected static final String RESOURCE_DESCRIPTOR = "ControlledSageMakerNotebookInstance";

  @JsonCreator
  public ControlledAwsSageMakerNotebookResource(
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
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("instanceType") String instanceType,
      @JsonProperty("defaultBucket")
          ControlledAwsSageMakerNotebookAttributes.DefaultBucket defaultBucket,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region) {
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
        privateResourceState,
        resourceLineage,
        properties,
        createdByEmail,
        createdDate,
        lastUpdatedByEmail,
        lastUpdatedDate,
        region);
    this.instanceId = instanceId;
    this.instanceType = instanceType;
    this.defaultBucket = defaultBucket;
    validate();
  }

  private ControlledAwsSageMakerNotebookResource(
      ControlledResourceFields common,
      String instanceId,
      String instanceType,
      ControlledAwsSageMakerNotebookAttributes.DefaultBucket defaultBucket) {
    super(common);
    this.instanceId = instanceId;
    this.instanceType = instanceType;
    this.defaultBucket = defaultBucket;
    validate();
  }

  public static Builder builder() {
    return new Builder();
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
            .addParameter("instanceId", getInstanceId()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();

    flight.addStep(new CreateAwsSageMakerNotebookStep(this), cloudRetry);
    flight.addStep(new WaitForAwsSageMakerNotebookInService(), cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    // TODO(TERRA-382): Implement and add delete flight steps. todo-dex
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public ControlledAwsSageMakerNotebookAttributes.DefaultBucket getDefaultBucket() {
    return defaultBucket;
  }

  public ApiAwsSageMakerNotebookAttributes toApiAttributes() {
    return new ApiAwsSageMakerNotebookAttributes()
        .instanceId(getInstanceId())
        .region(getRegion())
        .instanceType(getInstanceType())
        .defaultBucket(
            Optional.ofNullable(getDefaultBucket())
                .map(ControlledAwsSageMakerNotebookAttributes.DefaultBucket::toApi)
                .orElse(null));
  }

  public ApiAwsSageMakerNotebookResource toApiResource() {
    return new ApiAwsSageMakerNotebookResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AWS_SAGEMAKER_NOTEBOOK;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAwsSageMakerNotebookAttributes(
            getInstanceId(), getRegion(), getInstanceType(), getDefaultBucket()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.awsSagemakerNotebook(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK
        || getResourceFamily() != WsmResourceFamily.AWS_SAGEMAKER_NOTEBOOK
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AWS_SAGEMAKER_NOTEBOOK");
    }
    if (!getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      throw new BadRequestException(
          "Access scope must be private. Shared SageMaker Notebook instances are not yet implemented.");
    }
    ResourceValidationUtils.checkFieldNonNull(getInstanceId(), "instanceId", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getRegion(), "region", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(
        getInstanceType(), "instanceType", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.validateSageMakerNotebookInstanceId(getInstanceId());
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String instanceId;
    private String region;
    private String instanceType;
    private ControlledAwsSageMakerNotebookAttributes.DefaultBucket defaultBucket;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder region(String region) {
      this.region = region;
      return this;
    }

    public Builder instanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder defaultBucket(
        ControlledAwsSageMakerNotebookAttributes.DefaultBucket defaultBucket) {
      this.defaultBucket = defaultBucket;
      return this;
    }

    public Builder defaultBucket(ApiAwsSagemakerNotebookDefaultBucket defaultBucket) {
      if (defaultBucket != null) {
        this.defaultBucket =
            new ControlledAwsSageMakerNotebookAttributes.DefaultBucket(defaultBucket);
      }
      return this;
    }

    public ControlledAwsSageMakerNotebookResource build() {
      return new ControlledAwsSageMakerNotebookResource(
          common, instanceId, instanceType, defaultBucket);
    }
  }
}
