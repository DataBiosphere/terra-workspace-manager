package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.common.exception.ApiException;
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
import bio.terra.workspace.generated.model.ApiAwsStorageFolderAttributes;
import bio.terra.workspace.generated.model.ApiAwsStorageFolderResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.*;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class ControlledAwsStorageFolderResource extends ControlledResource {
  private final String bucketName;
  private final String prefix;

  @JsonCreator
  public ControlledAwsStorageFolderResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("prefix") String prefix) {
    super(resourceFields, controlledResourceFields);
    this.bucketName = bucketName;
    this.prefix = prefix;
    validate();
  }

  private ControlledAwsStorageFolderResource(
      ControlledResourceFields common, String bucketName, String prefix) {
    super(common);
    this.bucketName = bucketName;
    this.prefix = prefix;
    validate();
  }

  public ControlledAwsStorageFolderResource(
      DbResource dbResource, String bucketName, String prefix) {
    super(dbResource);
    this.bucketName = bucketName;
    this.prefix = prefix;
    validate();
  }

  public static ControlledAwsStorageFolderResource.Builder builder() {
    return new ControlledAwsStorageFolderResource.Builder();
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

  public String getBucketName() {
    return bucketName;
  }

  public String getPrefix() {
    return prefix;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AWS_STORAGE_FOLDER;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.GLOBAL)
            .addParameter("prefix", prefix));
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
        new ValidateAwsStorageFolderCreateStep(this, flightBeanBag.getAwsCloudContextService()),
        cloudRetry);
    flight.addStep(
        new CreateAwsStorageFolderStep(
            this, flightBeanBag.getAwsCloudContextService(), userRequest),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAwsStorageFolderStep(this, flightBeanBag.getAwsCloudContextService()),
        RetryRules.cloud());
  }

  /** {@inheritDoc} */
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    throw new ApiException("addUpdateSteps NotImplemented");
  }

  public ApiAwsStorageFolderAttributes toApiAttributes() {
    return new ApiAwsStorageFolderAttributes()
        .bucketName(getBucketName())
        .prefix(getPrefix())
        .region(getRegion());
  }

  public ApiAwsStorageFolderResource toApiResource() {
    return new ApiAwsStorageFolderResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAwsStorageFolderAttributes(getBucketName(), getPrefix(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.awsStorageFolder(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER
        || getResourceFamily() != WsmResourceFamily.AWS_STORAGE_FOLDER
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AWS_STORAGE_FOLDER");
    }
    if ((getBucketName() == null) || (getPrefix() == null) || (getRegion() == null)) {
      throw new MissingRequiredFieldException(
          "Missing required field for ControlledAwsStorageFolderResource.");
    }
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String bucketName;
    private String prefix;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public ControlledAwsStorageFolderResource build() {
      return new ControlledAwsStorageFolderResource(common, bucketName, prefix);
    }
  }
}
