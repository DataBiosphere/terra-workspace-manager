package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

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
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderAttributes;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
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

public class ControlledAwsS3StorageFolderResource extends ControlledResource {
  private final String s3BucketName;
  private final String prefix;

  @JsonCreator
  public ControlledAwsS3StorageFolderResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("s3BucketName") String s3BucketName,
      @JsonProperty("prefix") String prefix) {
    super(resourceFields, controlledResourceFields);
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
    validate();
  }

  private ControlledAwsS3StorageFolderResource(
      ControlledResourceFields common, String s3BucketName, String prefix) {
    super(common);
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
    validate();
  }

  public ControlledAwsS3StorageFolderResource(
      DbResource dbResource, String s3BucketName, String prefix) {
    super(dbResource);
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
    validate();
  }

  public static ControlledAwsS3StorageFolderResource.Builder builder() {
    return new ControlledAwsS3StorageFolderResource.Builder();
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

  public String getS3BucketName() {
    return s3BucketName;
  }

  public String getPrefix() {
    return prefix;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AWS_S3_STORAGE_FOLDER;
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
    // TODO: get default region from user profile
    flight.addStep(
        new ValidateAwsS3StorageFolderCreationStep(this, flightBeanBag.getAwsCloudContextService()),
        cloudRetry);
    flight.addStep(
        new CreateAwsS3StorageFolderStep(this, flightBeanBag.getAwsCloudContextService()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(new DeleteAwsS3StorageFolderStep(this), RetryRules.cloud());
  }

  /** {@inheritDoc} */
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    throw new ApiException("addUpdateSteps NotImplemented");
  }

  public ApiAwsS3StorageFolderAttributes toApiAttributes() {
    return new ApiAwsS3StorageFolderAttributes()
        .s3BucketName(getS3BucketName())
        .prefix(getPrefix());
  }

  public ApiAwsS3StorageFolderResource toApiResource() {
    return new ApiAwsS3StorageFolderResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAwsS3StorageFolderAttributes(getS3BucketName(), getPrefix(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.awsS3StorageFolder(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER
        || getResourceFamily() != WsmResourceFamily.AWS_S3_STORAGE_FOLDER
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AWS_S3_STORAGE_FOLDER");
    }
    if (getPrefix() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field prefix for ControlledAwsS3StorageFolderResource.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field region for ControlledAwsS3StorageFolderResource.");
    }

    // TODO-Dex
    ResourceValidationUtils.validateBucketNameAllowsUnderscore(prefix);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String s3BucketName;
    private String prefix;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder s3BucketName(String s3BucketName) {
      this.s3BucketName = s3BucketName;
      return this;
    }

    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public ControlledAwsS3StorageFolderResource build() {
      return new ControlledAwsS3StorageFolderResource(common, s3BucketName, prefix);
    }
  }
}
