package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureVmAttributes;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceRegionStep;
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
import java.util.UUID;

public class ControlledAzureVmResource extends ControlledResource {
  private final String vmName;
  private final String vmSize;
  private final String vmImage;

  private final UUID ipId;
  private final UUID networkId;
  private final UUID diskId;

  @JsonCreator
  public ControlledAzureVmResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("vmName") String vmName,
      @JsonProperty("vmSize") String vmSize,
      @JsonProperty("vmImage") String vmImage,
      @JsonProperty("ipId") UUID ipId,
      @JsonProperty("networkId") UUID networkId,
      @JsonProperty("diskId") UUID diskId) {
    super(resourceFields, controlledResourceFields);
    this.vmName = vmName;
    this.vmSize = vmSize;
    this.vmImage = vmImage;
    this.ipId = ipId;
    this.networkId = networkId;
    this.diskId = diskId;
    validate();
  }

  private ControlledAzureVmResource(
      ControlledResourceFields common,
      String vmName,
      String vmSize,
      String vmImage,
      UUID ipId,
      UUID networkId,
      UUID diskId) {
    super(common);
    this.vmName = vmName;
    this.vmImage = vmImage;
    this.vmSize = vmSize;
    this.ipId = ipId;
    this.networkId = networkId;
    this.diskId = diskId;
    validate();
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

  public String getVmName() {
    return vmName;
  }

  public String getVmSize() {
    return vmSize;
  }

  public String getVmImage() {
    return vmImage;
  }

  public UUID getIpId() {
    return ipId;
  }

  public UUID getNetworkId() {
    return networkId;
  }

  public UUID getDiskId() {
    return diskId;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_VM;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_VM;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("vmName", getVmName()));
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
        new GetAzureVmStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureNetworkInterfaceStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getResourceDao(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService()),
        cloudRetry);
    flight.addStep(
        new CreateAzureVmStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getResourceDao()),
        cloudRetry);
    flight.addStep(
        new UpdateControlledResourceRegionStep(flightBeanBag.getResourceDao(), getResourceId()),
        RetryRules.shortDatabase());
    flight.addStep(
        new AssignManagedIdentityAzureVmStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getSamService(),
            this),
        cloudRetry);
    flight.addStep(
        new EnableVmLoggingStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new RemoveManagedIdentitiesAzureVmStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
    flight.addStep(
        new DeleteAzureVmStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
    flight.addStep(
        new DeleteAzureNetworkInterfaceStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public ApiAzureVmAttributes toApiAttributes() {
    return new ApiAzureVmAttributes()
        .vmName(getVmName())
        .region(getRegion())
        .vmSize(getVmSize())
        .vmImage(getVmImage())
        .ipId(getIpId())
        .diskId(getDiskId())
        .networkId(getNetworkId());
  }

  public ApiAzureVmResource toApiResource() {
    return new ApiAzureVmResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureVm(toApiAttributes());
    return union;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureVmAttributes(
            getVmName(),
            getRegion(),
            getVmSize(),
            getVmImage(),
            getIpId(),
            getNetworkId(),
            getDiskId()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_VM
        || getResourceFamily() != WsmResourceFamily.AZURE_VM
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AZURE_VM");
    }
    if (getVmName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required vmName field for ControlledAzureVm.");
    }
    if (getVmSize() == null) {
      throw new MissingRequiredFieldException(
          "Missing required valid vmSize field for ControlledAzureVm.");
    }
    if (getVmImage() == null) {
      throw new MissingRequiredFieldException(
          "Missing required valid vmImage field for ControlledAzureVm.");
    }
    ResourceValidationUtils.validateAzureIPorSubnetName(getVmName());
    ResourceValidationUtils.validateAzureVmSize(getVmSize());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureVmResource that = (ControlledAzureVmResource) o;

    return vmName.equals(that.getVmName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + vmName.hashCode();
    return result;
  }

  public static ControlledAzureVmResource.Builder builder() {
    return new ControlledAzureVmResource.Builder();
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String vmName;
    private String region;
    private String vmSize;
    private String vmImage;
    private UUID ipId;
    private UUID networkId;
    private UUID diskId;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureVmResource.Builder vmName(String vmName) {
      this.vmName = vmName;
      return this;
    }

    public ControlledAzureVmResource.Builder vmSize(String vmSize) {
      this.vmSize = vmSize;
      return this;
    }

    public ControlledAzureVmResource.Builder vmImage(String vmImage) {
      this.vmImage = vmImage;
      return this;
    }

    public ControlledAzureVmResource.Builder ipId(UUID ipId) {
      this.ipId = ipId;
      return this;
    }

    public ControlledAzureVmResource.Builder networkId(UUID networkId) {
      this.networkId = networkId;
      return this;
    }

    public ControlledAzureVmResource.Builder diskId(UUID diskId) {
      this.diskId = diskId;
      return this;
    }

    public ControlledAzureVmResource build() {
      return new ControlledAzureVmResource(
          common, vmName, vmSize, vmImage, ipId, networkId, diskId);
    }
  }
}
