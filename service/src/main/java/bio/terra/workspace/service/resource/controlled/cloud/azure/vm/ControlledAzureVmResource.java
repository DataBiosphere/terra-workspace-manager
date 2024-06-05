package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureVmAttributes;
import bio.terra.workspace.generated.model.ApiAzureVmPriority;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiAzureVmUserAssignedIdentities;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureVmResource extends ControlledResource {
  private final String vmName;
  private final String vmSize;
  private final String vmImage;
  private final UUID diskId;
  private final ApiAzureVmPriority priority;
  private final List<String> userAssignedIdentities;

  @JsonCreator
  public ControlledAzureVmResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("vmName") String vmName,
      @JsonProperty("vmSize") String vmSize,
      @JsonProperty("vmImage") String vmImage,
      @JsonProperty("diskId") UUID diskId,
      @JsonProperty("priority") ApiAzureVmPriority priority,
      @JsonProperty("userAssignedIdentities") List<String> userAssignedIdentities) {
    super(resourceFields, controlledResourceFields);
    this.vmName = vmName;
    this.vmSize = vmSize;
    this.vmImage = vmImage;
    this.diskId = diskId;
    this.priority = priority;
    this.userAssignedIdentities = userAssignedIdentities;
    validate();
  }

  private ControlledAzureVmResource(
      ControlledResourceFields common,
      String vmName,
      String vmSize,
      String vmImage,
      UUID diskId,
      ApiAzureVmPriority priority,
      List<String> userAssignedIdentities) {
    super(common);
    this.vmName = vmName;
    this.vmImage = vmImage;
    this.vmSize = vmSize;
    this.diskId = diskId;
    this.priority = priority;
    this.userAssignedIdentities = userAssignedIdentities;
    validate();
  }

  // -- getters used in serialization --

  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @Override
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

  public UUID getDiskId() {
    return diskId;
  }

  public ApiAzureVmPriority getPriority() {
    return priority;
  }

  public List<String> getUserAssignedIdentities() {
    return userAssignedIdentities;
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
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService()),
        cloudRetry);
    flight.addStep(
        new CreateAzureVmStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getResourceDao()),
        cloudRetry);
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
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService()),
        cloudRetry);
    flight.addStep(
        new InstallCustomVmExtension(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), new VmExtensionHelper()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(
        new RemoveManagedIdentitiesAzureVmStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        new DeleteAzureVmStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        new DeleteAzureNetworkInterfaceStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this));
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureVmAttributes toApiAttributes() {
    // VMs default to Regular priority if not specified
    var priority = Optional.ofNullable(getPriority()).orElse(ApiAzureVmPriority.REGULAR);
    var userAssigedIdentities = new ApiAzureVmUserAssignedIdentities();
    if (CollectionUtils.isNotEmpty(getUserAssignedIdentities())) {
      userAssigedIdentities.addAll(getUserAssignedIdentities());
    }
    return new ApiAzureVmAttributes()
        .vmName(getVmName())
        .region(getRegion())
        .vmSize(getVmSize())
        .vmImage(getVmImage())
        .diskId(getDiskId())
        .priority(priority)
        .userAssignedIdentities(userAssigedIdentities);
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
            getVmName(), getRegion(), getVmSize(), getVmImage(), getDiskId()));
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
    AzureResourceValidationUtils.validateAzureIPorSubnetName(getVmName());
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
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

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
    private String vmSize;
    private String vmImage;
    private UUID diskId;
    private ApiAzureVmPriority priority;
    private List<String> userAssignedIdentities;

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

    public ControlledAzureVmResource.Builder diskId(UUID diskId) {
      this.diskId = diskId;
      return this;
    }

    public ControlledAzureVmResource.Builder priority(ApiAzureVmPriority priority) {
      this.priority = priority;
      return this;
    }

    public ControlledAzureVmResource.Builder userAssignedIdentities(
        List<String> userAssignedIdentities) {
      this.userAssignedIdentities = userAssignedIdentities;
      return this;
    }

    public ControlledAzureVmResource build() {
      return new ControlledAzureVmResource(
          common, vmName, vmSize, vmImage, diskId, priority, userAssignedIdentities);
    }
  }
}
