package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceAttributes;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
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
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ControlledFlexibleResource extends ControlledResource {
  private final String typeNamespace;
  private final String type;
  @Nullable private final String data;

  @JsonCreator
  public ControlledFlexibleResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("typeNamespace") String typeNamespace,
      @JsonProperty("type") String type,
      @Nullable @JsonProperty("data") String data) {
    super(resourceFields, controlledResourceFields);
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
    validate();
  }

  // Constructor for the builder
  private ControlledFlexibleResource(
      ControlledResourceFields common, String typeNamespace, String type, @Nullable String data) {
    super(common);
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
    validate();
  }

  public static ControlledFlexibleResource.Builder builder() {
    return new ControlledFlexibleResource.Builder();
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

  public String getTypeNamespace() {
    return typeNamespace;
  }

  public String getType() {
    return type;
  }

  @Nullable
  public String getData() {
    return data;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.FLEXIBLE_RESOURCE;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.empty();
  }

  // There is no associated cloud resource. Thus, no steps are required.
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {}

  // There is no associated cloud resource. Thus, no steps are required.
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {}

  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    // TODO (PF-2539): Include when updating endpoint is added.
    //    RetryRule dbRetry = RetryRules.shortDatabase();
    //    flight.addStep(UpdateControlledFlexibleResourceAttributesStep, dbRetry);
  }

  public ApiFlexibleResourceAttributes toApiAttributes() {
    return new ApiFlexibleResourceAttributes()
        .typeNamespace(getTypeNamespace())
        .type(getType())
        .data(getData());
  }

  public ApiFlexibleResource toApiResource() {
    return new ApiFlexibleResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new FlexibleResourceAttributes(getTypeNamespace(), getType(), getData()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().flexibleResource(toApiAttributes());
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE
        || getResourceFamily() != WsmResourceFamily.FLEXIBLE_RESOURCE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled FLEXIBLE_RESOURCE");
    }

    if (getTypeNamespace() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field typeNamespace for Flexible Resource");
    }

    if (getType() == null) {
      throw new MissingRequiredFieldException("Missing required field type for Flexible Resource");
    }

    // Limit to 5 kilobytes in size.
    ResourceValidationUtils.validateFlexResourceDataSize(getData());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ControlledFlexibleResource that = (ControlledFlexibleResource) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(type, that.type)
        .append(data, that.data)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(19, 41)
        .appendSuper(super.hashCode())
        .append(type)
        .append(data)
        .toHashCode();
  }

  public static class Builder {
    private ControlledResourceFields common;

    private String typeNamespace;
    private String type;
    @Nullable private String data;

    public ControlledFlexibleResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledFlexibleResource.Builder typeNamespace(String typeNamespace) {
      this.typeNamespace = typeNamespace;
      return this;
    }

    public ControlledFlexibleResource.Builder type(String type) {
      this.type = type;
      return this;
    }

    public ControlledFlexibleResource.Builder data(@Nullable String data) {
      this.data = data;
      return this;
    }

    public ControlledFlexibleResource build() {
      return new ControlledFlexibleResource(common, typeNamespace, type, data);
    }
  }
}
