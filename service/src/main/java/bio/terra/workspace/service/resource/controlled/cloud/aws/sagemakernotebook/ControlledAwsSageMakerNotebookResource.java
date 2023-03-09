package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.NotImplementedException;
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
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook.ControlledAwsSageMakerNotebookAttributes.DefaultBucket;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class ControlledAwsSageMakerNotebookResource extends ControlledResource {
  private final String instanceId;
  private final String instanceType;
  private final DefaultBucket defaultBucket;

  protected static final String RESOURCE_DESCRIPTOR = "ControlledSageMakerNotebookInstance";

  @JsonCreator
  public ControlledAwsSageMakerNotebookResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("instanceType") String instanceType,
      @JsonProperty("defaultBucket") DefaultBucket defaultBucket) {
    super(resourceFields, controlledResourceFields);
    this.instanceId = instanceId;
    this.instanceType = instanceType;
    this.defaultBucket = defaultBucket;
    validate();
  }

  private ControlledAwsSageMakerNotebookResource(
      ControlledResourceFields common,
      String instanceId,
      String instanceType,
      DefaultBucket defaultBucket) {
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

  // -- getters used in serialization --
  @JsonProperty("wsmResourceFields")
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @JsonProperty("wsmControlledResourceFields")
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public DefaultBucket getDefaultBucket() {
    return defaultBucket;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AWS_SAGEMAKER_NOTEBOOK;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
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
    flight.addStep(
        new WaitForAwsSageMakerNotebookStatusStep(this, NotebookInstanceStatus.IN_SERVICE),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight , FlightBeanBag flightBeanBag) {
    throw new ApiException("addUpdateSteps NotImplemented");
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();
    flight.addStep(new DeleteAwsSageMakerNotebookStep(this), cloudRetry);
    flight.addStep(new WaitForAwsSageMakerNotebookStatusStep(this, null), cloudRetry);
  }

  public ApiAwsSageMakerNotebookAttributes toApiAttributes() {
    return new ApiAwsSageMakerNotebookAttributes()
        .instanceId(getInstanceId())
        .region(getRegion())
        .instanceType(getInstanceType())
        .defaultBucket(
            Optional.ofNullable(getDefaultBucket()).map(DefaultBucket::toApi).orElse(null));
  }

  public ApiAwsSageMakerNotebookResource toApiResource() {
    return new ApiAwsSageMakerNotebookResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
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
    private String instanceType;
    private DefaultBucket defaultBucket;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder instanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder defaultBucket(DefaultBucket defaultBucket) {
      this.defaultBucket = defaultBucket;
      return this;
    }

    public Builder defaultBucket(ApiAwsSagemakerNotebookDefaultBucket defaultBucket) {
      if (defaultBucket != null) {
        this.defaultBucket = new DefaultBucket(defaultBucket);
      }
      return this;
    }

    public ControlledAwsSageMakerNotebookResource build() {
      return new ControlledAwsSageMakerNotebookResource(
          common, instanceId, instanceType, defaultBucket);
    }
  }
}
