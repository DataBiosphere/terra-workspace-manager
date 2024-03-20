package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerAttributes;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerResource;
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

public class ControlledAzureStorageContainerResource extends ControlledResource {
  private final String storageContainerName;

  @JsonCreator
  public ControlledAzureStorageContainerResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("storageContainerName") String storageContainerName) {
    super(resourceFields, controlledResourceFields);
    this.storageContainerName = storageContainerName;
    validate();
  }

  private ControlledAzureStorageContainerResource(
      ControlledResourceFields common, String storageContainerName) {
    super(common);
    this.storageContainerName = storageContainerName;
    validate();
  }

  public static ControlledAzureStorageContainerResource.Builder builder() {
    return new ControlledAzureStorageContainerResource.Builder();
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

  public String getStorageContainerName() {
    return storageContainerName;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_STORAGE_CONTAINER;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("storageContainerName", getStorageContainerName()));
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
        new VerifyAzureStorageContainerCanBeCreatedStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getResourceDao(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            this,
            flightBeanBag.getWorkspaceService()),
        cloudRetry);
    flight.addStep(
        new CreateAzureStorageContainerStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(
        new DeleteAzureStorageContainerStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            this,
            flightBeanBag.getWorkspaceService()));
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureStorageContainerAttributes toApiAttributes() {
    return new ApiAzureStorageContainerAttributes().storageContainerName(getStorageContainerName());
  }

  public ApiAzureStorageContainerResource toApiResource() {
    return new ApiAzureStorageContainerResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureStorageContainerAttributes(getStorageContainerName()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureStorageContainer(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER
        || getResourceFamily() != WsmResourceFamily.AZURE_STORAGE_CONTAINER
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AZURE_STORAGE_CONTAINER");
    }

    if (getStorageContainerName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required storage container name field for ControlledAzureStorageContainer.");
    }

    AzureResourceValidationUtils.validateAzureStorageContainerName(getStorageContainerName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureStorageContainerResource that = (ControlledAzureStorageContainerResource) o;

    return storageContainerName.equals(that.getStorageContainerName());
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAzureStorageContainerResource that = (ControlledAzureStorageContainerResource) o;

    return storageContainerName.equals(that.getStorageContainerName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + storageContainerName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String storageContainerName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureStorageContainerResource.Builder storageContainerName(
        String storageContainerName) {
      this.storageContainerName = storageContainerName;
      return this;
    }

    public ControlledAzureStorageContainerResource build() {
      return new ControlledAzureStorageContainerResource(common, storageContainerName);
    }
  }
}
