package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAwsBucketAttributes;
import bio.terra.workspace.generated.model.ApiAwsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.*;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

public class ControlledAwsBucketResource extends ControlledResource {
  private final String s3BucketName;
  private final String prefix;

  @JsonCreator
  public ControlledAwsBucketResource(
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

  private ControlledAwsBucketResource(
      ControlledResourceFields common, String s3BucketName, String prefix) {
    super(common);
    this.s3BucketName = s3BucketName;
    this.prefix = prefix;
    validate();
  }

  public static ControlledAwsBucketResource.Builder builder() {
    return new ControlledAwsBucketResource.Builder();
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
    return WsmResourceType.CONTROLLED_AWS_BUCKET;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AWS_BUCKET;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.GLOBAL)
            .addParameter("prefix", getPrefix()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();

    flight.addStep(new ValidateAwsBucketCreationStep(this), cloudRetry);
    flight.addStep(new CreateAwsBucketStep(this), cloudRetry);

    // Check if the user requested that the bucket be seeded with sample data.
    ApiAwsBucketCreationParameters creationParameters =
        flight
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
                ApiAwsBucketCreationParameters.class);

    if (creationParameters != null && creationParameters.isSeed()) {
      // Check that we actually have example data to seed with.
      List<AwsConfiguration.AwsBucketSeedFile> seedFiles =
          flightBeanBag.getAwsConfiguration().getBucketSeedFiles();
      if (seedFiles != null && !seedFiles.isEmpty()) {
        flight.addStep(new SeedAwsBucketStep(seedFiles, this), cloudRetry);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    // TODO: Implement and add delete flight steps.
  }

  public ApiAwsBucketAttributes toApiAttributes() {
    return new ApiAwsBucketAttributes()
        .s3BucketName(getS3BucketName())
        .prefix(getPrefix())
        .region(getRegion());
  }

  public ApiAwsBucketResource toApiResource() {
    return new ApiAwsBucketResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAwsBucketAttributes(getS3BucketName(), getPrefix(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.awsBucket(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AWS_BUCKET
        || getResourceFamily() != WsmResourceFamily.AWS_BUCKET
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AWS_BUCKET");
    }
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String s3BucketName;
    private String prefix;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAwsBucketResource.Builder s3BucketName(String s3BucketName) {
      this.s3BucketName = s3BucketName;
      return this;
    }

    public ControlledAwsBucketResource.Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public ControlledAwsBucketResource build() {
      return new ControlledAwsBucketResource(common, s3BucketName, prefix);
    }
  }
}
