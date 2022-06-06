package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.*;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import java.util.UUID;

public class ControlledAzureStorageContainerResource extends ControlledResource {
  private final String storageAccountName;
  private final String storageContainerName;

  @JsonCreator
  public ControlledAzureStorageContainerResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") String applicationId,
      @JsonProperty("storageAccountName") String storageAccountName,
      @JsonProperty("storageContainerName") String storageContainerName) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState);
    this.storageAccountName = storageAccountName;
    this.storageContainerName = storageContainerName;
    validate();
  }

  private ControlledAzureStorageContainerResource(
      ControlledResourceFields common, String storageAccountName, String storageContainerName) {
    super(common);
    this.storageAccountName = storageAccountName;
    this.storageContainerName = storageContainerName;
    validate();
  }

  public static ControlledAzureStorageContainerResource.Builder builder() {
    return new ControlledAzureStorageContainerResource.Builder();
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

  /** {@inheritDoc} */
  @Override
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("storageAccountName", getStorageAccountName())
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
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureStorageContainerStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureStorageContainerStep(
                flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }

  public ApiAzureStorageContainerAttributes toApiAttributes() {
    return new ApiAzureStorageContainerAttributes()
        .storageAccountName(getStorageAccountName())
        .storageContainerName(getStorageContainerName());
  }

  public ApiAzureStorageContainerResource toApiResource() {
    return new ApiAzureStorageContainerResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_STORAGE_CONTAINER;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureStorageContainerAttributes(getStorageAccountName(), getStorageContainerName()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureStorageContainer(toApiAttributes());
    return union;
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.azureStorageContainer(toApiResource());
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

    if (getStorageAccountName() == null) {
      throw new MissingRequiredFieldException(
              "Missing required storage account name field for ControlledAzureStorageContainer.");
    }

    if (getStorageContainerName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required storage container name field for ControlledAzureStorageContainer.");
    }

    ResourceValidationUtils.validateStorageAccountName(getStorageAccountName());
    ResourceValidationUtils.validateStorageContainerName(getStorageContainerName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureStorageContainerResource that = (ControlledAzureStorageContainerResource) o;

    return storageAccountName.equals(that.getStorageAccountName()) && storageContainerName.equals(that.getStorageContainerName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + storageAccountName.hashCode();
    result = 31 * result + storageContainerName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String storageAccountName;
    private String storageContainerName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureStorageContainerResource.Builder storageAccountName(String storageAccountName) {
      this.storageAccountName = storageAccountName;
      return this;
    }

    public ControlledAzureStorageContainerResource.Builder storageContainerName(String storageContainerName) {
      this.storageContainerName = storageContainerName;
      return this;
    }

    public ControlledAzureStorageContainerResource build() {
      return new ControlledAzureStorageContainerResource(common, storageAccountName, storageContainerName);
    }
  }
}
