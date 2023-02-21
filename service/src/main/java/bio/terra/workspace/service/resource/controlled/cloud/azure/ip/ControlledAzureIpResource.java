package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureIpAttributes;
import bio.terra.workspace.generated.model.ApiAzureIpResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
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

public class ControlledAzureIpResource extends ControlledResource {
  private final String ipName;

  @JsonCreator
  public ControlledAzureIpResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("ipName") String ipName) {
    super(resourceFields, controlledResourceFields);
    this.ipName = ipName;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureIpResource(ControlledResourceFields common, String ipName) {
    super(common);
    this.ipName = ipName;
    validate();
  }

  public static ControlledAzureIpResource.Builder builder() {
    return new ControlledAzureIpResource.Builder();
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

  public String getIpName() {
    return ipName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_IP;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_IP;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("ipName", getIpName()));
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
        new GetAzureIpStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureIpStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureIpStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public ApiAzureIpAttributes toApiAttributes() {
    return new ApiAzureIpAttributes().ipName(getIpName()).region(getRegion());
  }

  public ApiAzureIpResource toApiResource() {
    return new ApiAzureIpResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledAzureIpAttributes(getIpName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureIp(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_IP
        || getResourceFamily() != WsmResourceFamily.AZURE_IP
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_IP");
    }
    if (getIpName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required ipName field for ControlledAzureIP.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureIP.");
    }
    ResourceValidationUtils.validateAzureRegion(getRegion());
    ResourceValidationUtils.validateAzureIPorSubnetName(getIpName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureIpResource that = (ControlledAzureIpResource) o;

    return ipName.equals(that.getIpName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + ipName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String ipName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureIpResource.Builder ipName(String ipName) {
      this.ipName = ipName;
      return this;
    }

    public ControlledAzureIpResource build() {
      return new ControlledAzureIpResource(common, ipName);
    }
  }
}
