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
import bio.terra.workspace.generated.model.ApiAzureStorageContainerAttributes;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceRegionStep;
import bio.terra.workspace.service.resource.controlled.model.*;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureStorageContainerResource extends ControlledResource {
  private final UUID storageAccountId;
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
      @JsonProperty("storageAccountId") UUID storageAccountId,
      @JsonProperty("storageContainerName") String storageContainerName,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region) {
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
        privateResourceState,
        resourceLineage,
        properties,
        createdByEmail,
        createdDate,
        lastUpdatedByEmail,
        lastUpdatedDate,
        region);
    this.storageAccountId = storageAccountId;
    this.storageContainerName = storageContainerName;
    validate();
  }

  private ControlledAzureStorageContainerResource(
      ControlledResourceFields common, UUID storageAccountId, String storageContainerName) {
    super(common);
    this.storageAccountId = storageAccountId;
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
            .addParameter(
                "storageAccountId",
                Optional.ofNullable(getStorageAccountId()).map(UUID::toString).orElse(null))
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
            userRequest,
            this),
        cloudRetry);
    flight.addStep(
        new CreateAzureStorageContainerStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new UpdateControlledResourceRegionStep(
            flightBeanBag.getResourceDao(), getWorkspaceId(), getResourceId()),
        RetryRules.shortDatabase());
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureStorageContainerStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getResourceDao(),
            flightBeanBag.getLandingZoneApiDispatch(),
            this),
        RetryRules.cloud());
  }

  public UUID getStorageAccountId() {
    return storageAccountId;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }

  public ApiAzureStorageContainerAttributes toApiAttributes() {
    return new ApiAzureStorageContainerAttributes()
        .storageAccountId(getStorageAccountId())
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
        new ControlledAzureStorageContainerAttributes(
            getStorageAccountId(), getStorageContainerName()));
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

    ResourceValidationUtils.validateStorageContainerName(getStorageContainerName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureStorageContainerResource that = (ControlledAzureStorageContainerResource) o;

    return (storageAccountId == null || storageAccountId.equals(that.getStorageAccountId()))
        && storageContainerName.equals(that.getStorageContainerName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    if (storageAccountId != null) {
      result = 31 * result + storageAccountId.hashCode();
    }
    result = 31 * result + storageContainerName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private UUID storageAccountId;
    private String storageContainerName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureStorageContainerResource.Builder storageAccountId(UUID storageAccountId) {
      this.storageAccountId = storageAccountId;
      return this;
    }

    public ControlledAzureStorageContainerResource.Builder storageContainerName(
        String storageContainerName) {
      this.storageContainerName = storageContainerName;
      return this;
    }

    public ControlledAzureStorageContainerResource build() {
      return new ControlledAzureStorageContainerResource(
          common, storageAccountId, storageContainerName);
    }
  }
}
