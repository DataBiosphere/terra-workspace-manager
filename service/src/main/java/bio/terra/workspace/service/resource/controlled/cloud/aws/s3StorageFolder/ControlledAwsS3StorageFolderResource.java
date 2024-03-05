package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.FlightMap;
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
import bio.terra.workspace.service.resource.AwsResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.model.*;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

public class ControlledAwsS3StorageFolderResource extends ControlledResource {
  private static final String RESOURCE_DESCRIPTOR = "ControlledAwsS3StorageFolder";
  private final String bucketName;
  private final String prefix;

  @JsonCreator
  public ControlledAwsS3StorageFolderResource(
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

  private ControlledAwsS3StorageFolderResource(
      ControlledResourceFields common, String bucketName, String prefix) {
    super(common);
    this.bucketName = bucketName;
    this.prefix = prefix;
    validate();
  }

  public ControlledAwsS3StorageFolderResource(
      DbResource dbResource, String bucketName, String prefix) {
    super(dbResource);
    this.bucketName = bucketName;
    this.prefix = prefix;
    validate();
  }

  public static ControlledAwsS3StorageFolderResource.Builder builder() {
    return new ControlledAwsS3StorageFolderResource.Builder();
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
            .addParameter("bucketName", bucketName)
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
        new ValidateAwsS3StorageFolderCreateStep(
            this, flightBeanBag.getAwsCloudContextService(), flightBeanBag.getSamService()),
        cloudRetry);
    flight.addStep(
        new CreateAwsS3StorageFolderStep(
            this, flightBeanBag.getAwsCloudContextService(), flightBeanBag.getSamService()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(
        new DeleteAwsS3StorageFolderStep(
            this, flightBeanBag.getAwsCloudContextService(), flightBeanBag.getSamService()));
  }

  /** {@inheritDoc} */
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    // TODO(TERRA-315) Determine if we need to support updating the storage folder prefix
  }

  public ApiAwsS3StorageFolderAttributes toApiAttributes() {
    return new ApiAwsS3StorageFolderAttributes().bucketName(bucketName).prefix(prefix);
  }

  public ApiAwsS3StorageFolderResource toApiResource() {
    return new ApiAwsS3StorageFolderResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledAwsS3StorageFolderAttributes(bucketName, prefix));
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
    ResourceValidationUtils.checkStringNonEmpty(bucketName, "bucketName", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkStringNonEmpty(prefix, "prefix", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkStringNonEmpty(getRegion(), "region", RESOURCE_DESCRIPTOR);
    AwsResourceValidationUtils.validateAwsS3StorageFolderName(prefix);
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

    public ControlledAwsS3StorageFolderResource build() {
      return new ControlledAwsS3StorageFolderResource(common, bucketName, prefix);
    }
  }
}
