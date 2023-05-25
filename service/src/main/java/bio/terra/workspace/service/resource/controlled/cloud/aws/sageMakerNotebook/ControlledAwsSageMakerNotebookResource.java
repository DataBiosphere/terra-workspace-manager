package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookAttributes;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.AwsResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class ControlledAwsSageMakerNotebookResource extends ControlledResource {
  private static final String RESOURCE_DESCRIPTOR = "ControlledAwsSageMakerNotebook";
  private final String instanceName;
  private final String instanceType;

  @JsonCreator
  public ControlledAwsSageMakerNotebookResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("instanceType") String instanceType) {
    super(resourceFields, controlledResourceFields);
    this.instanceName = instanceName;
    this.instanceType = instanceType;
    validate();
  }

  private ControlledAwsSageMakerNotebookResource(
      ControlledResourceFields common, String instanceName, String instanceType) {
    super(common);
    this.instanceName = instanceName;
    this.instanceType = instanceType;
    validate();
  }

  public ControlledAwsSageMakerNotebookResource(
      DbResource dbResource, String instanceName, String instanceType) {
    super(dbResource);
    this.instanceName = instanceName;
    this.instanceType = instanceType;
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

  public String getInstanceName() {
    return instanceName;
  }

  public String getInstanceType() {
    return instanceType;
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
            .addParameter("instanceName", instanceName));
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
        new CreateAwsSageMakerNotebookStep(
            this,
            flightBeanBag.getAwsCloudContextService(),
            userRequest,
            flightBeanBag.getSamService(),
            flightBeanBag.getCliConfiguration()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();
    boolean forceDelete =
        flight.getInputParameters().get(ControlledResourceKeys.FORCE_DELETE, Boolean.class);

    // Notebooks must be stopped before deletion. If requested, stop instance before delete attempt
    if (forceDelete) {
      flight.addStep(
          new StopAwsSageMakerNotebookStep(this, flightBeanBag.getAwsCloudContextService(), true),
          cloudRetry);
    } else {
      flight.addStep(
          new ValidateAwsSageMakerNotebookDeleteStep(
              this, flightBeanBag.getAwsCloudContextService()),
          cloudRetry);
    }

    flight.addStep(
        new DeleteAwsSageMakerNotebookStep(this, flightBeanBag.getAwsCloudContextService()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    // TODO(TERRA-223) Add support for UpdateAwsSageMakerNotebook
    throw new ApiException("addUpdateSteps NotImplemented");
  }

  public ApiAwsSageMakerNotebookAttributes toApiAttributes() {
    return new ApiAwsSageMakerNotebookAttributes()
        .instanceName(instanceName)
        .instanceType(instanceType);
  }

  public ApiAwsSageMakerNotebookResource toApiResource() {
    return new ApiAwsSageMakerNotebookResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAwsSageMakerNotebookAttributes(instanceName, instanceType));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.awsSageMakerNotebook(toApiAttributes());
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
          "Access scope must be private. Shared Sagemaker Notebook instances are not yet implemented.");
    }
    ResourceValidationUtils.checkFieldNonNull(instanceName, "instanceName", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(instanceType, "instanceType", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getRegion(), "region", RESOURCE_DESCRIPTOR);
    AwsResourceValidationUtils.validateAwsSageMakerNotebookName(instanceName);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String instanceName;
    private String instanceType;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder instanceName(String instanceName) {
      this.instanceName = instanceName;
      return this;
    }

    public Builder instanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public ControlledAwsSageMakerNotebookResource build() {
      return new ControlledAwsSageMakerNotebookResource(common, instanceName, instanceType);
    }
  }
}
