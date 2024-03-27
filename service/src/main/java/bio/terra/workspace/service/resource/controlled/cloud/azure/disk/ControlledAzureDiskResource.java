package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskAttributes;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
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
import java.util.List;
import java.util.Optional;

public class ControlledAzureDiskResource extends ControlledResource {
  private final String diskName;

  /** size is in GB */
  private final int size;

  @JsonCreator
  public ControlledAzureDiskResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("diskName") String diskName,
      @JsonProperty("size") int size) {
    super(resourceFields, controlledResourceFields);
    this.diskName = diskName;
    this.size = size;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureDiskResource(ControlledResourceFields common, String diskName, int size) {
    super(common);
    this.diskName = diskName;
    this.size = size;
    validate();
  }

  public static ControlledAzureDiskResource.Builder builder() {
    return new ControlledAzureDiskResource.Builder();
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

  public String getDiskName() {
    return diskName;
  }

  public int getSize() {
    return size;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_DISK;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_DISK;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("diskName", getDiskName()));
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
        new GetAzureDiskStep(flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureDiskStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(
        new GetAzureDiskAttachedVmStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        new DetachAzureDiskFromVmStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        new DeleteAzureDiskStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this));
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureDiskResource toApiResource() {
    return new ApiAzureDiskResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  private ApiAzureDiskAttributes toApiAttributes() {
    return new ApiAzureDiskAttributes().diskName(getDiskName()).region(getRegion());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureDiskAttributes(getDiskName(), getRegion(), getSize()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureDisk(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_DISK
        || getResourceFamily() != WsmResourceFamily.AZURE_DISK
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_DISK");
    }
    if (getDiskName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required diskName field for ControlledAzureDisk.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureDisk.");
    }
    if (getSize() <= 0) {
      throw new InconsistentFieldsException("Disk size must be a positive non-zero integer.");
    }
    AzureResourceValidationUtils.validateAzureDiskName(getDiskName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureDiskResource that = (ControlledAzureDiskResource) o;

    return diskName.equals(that.getDiskName());
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAzureDiskResource that = (ControlledAzureDiskResource) o;

    return diskName.equals(that.getDiskName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + diskName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String diskName;
    private int size;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureDiskResource.Builder diskName(String diskName) {
      this.diskName = diskName;
      return this;
    }

    public ControlledAzureDiskResource.Builder size(int size) {
      this.size = size;
      return this;
    }

    public ControlledAzureDiskResource build() {
      return new ControlledAzureDiskResource(common, diskName, size);
    }
  }
}
