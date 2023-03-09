package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureStorageAttributes;
import bio.terra.workspace.generated.model.ApiAzureStorageResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureStorageResource extends ControlledResource {
  private final String storageAccountName;

  @JsonCreator
  public ControlledAzureStorageResource(
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
      @JsonProperty("region") String region,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate) {

    super(
        ControlledResourceFields.builder()
            .workspaceUuid(workspaceId)
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .cloningInstructions(cloningInstructions)
            .assignedUser(assignedUser)
            .accessScope(accessScope)
            .managedBy(managedBy)
            .applicationId(applicationId)
            .privateResourceState(privateResourceState)
            .resourceLineage(resourceLineage)
            .properties(properties)
            .createdByEmail(createdByEmail)
            .createdDate(createdDate)
            .lastUpdatedByEmail(lastUpdatedByEmail)
            .lastUpdatedDate(lastUpdatedDate)
            .region(region)
            .build());
    this.storageAccountName = storageAccountName;
    validate();
  }

  private ControlledAzureStorageResource(
      ControlledResourceFields common, String storageAccountName, String region) {
    super(common);
    this.storageAccountName = storageAccountName;
    validate();
  }

  /*
   // TODO: PF-2512 remove constructor above and enable this constructor
   @JsonCreator
   public ControlledAzureStorageResource(
       @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
       @JsonProperty("wsmControlledResourceFields")
           WsmControlledResourceFields controlledResourceFields,
       @JsonProperty("storageAccountName") String storageAccountName) {
     super(resourceFields, controlledResourceFields);
     this.storageAccountName = storageAccountName;
     validate();
   }
  */

  private ControlledAzureStorageResource(
      ControlledResourceFields common, String storageAccountName) {
    super(common);
    this.storageAccountName = storageAccountName;
    validate();
  }

  public static ControlledAzureStorageResource.Builder builder() {
    return new ControlledAzureStorageResource.Builder();
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

  public String getStorageAccountName() {
    return storageAccountName;
  }

  // -- getters for backward compatibility --
  // TODO: PF-2512 Remove these getters
  public UUID getWorkspaceId() {
    return super.getWorkspaceId();
  }

  public UUID getResourceId() {
    return super.getResourceId();
  }

  public String getName() {
    return super.getName();
  }

  public String getDescription() {
    return super.getDescription();
  }

  public CloningInstructions getCloningInstructions() {
    return super.getCloningInstructions();
  }

  public Optional<String> getAssignedUser() {
    return super.getAssignedUser();
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return super.getPrivateResourceState();
  }

  public AccessScopeType getAccessScope() {
    return super.getAccessScope();
  }

  public ManagedByType getManagedBy() {
    return super.getManagedBy();
  }

  public String getApplicationId() {
    return super.getApplicationId();
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return super.getResourceLineage();
  }

  public ImmutableMap<String, String> getProperties() {
    return super.getProperties();
  }

  public String getCreatedByEmail() {
    return super.getCreatedByEmail();
  }

  public OffsetDateTime getCreatedDate() {
    return super.getCreatedDate();
  }

  public String getLastUpdatedByEmail() {
    return super.getLastUpdatedByEmail();
  }

  public OffsetDateTime getLastUpdatedDate() {
    return super.getLastUpdatedDate();
  }

  public String getRegion() {
    return super.getRegion();
  }

  // -- getters not included in serialization --

  @JsonIgnore
  public String getStorageAccountEndpoint() {
    return String.format(Locale.ROOT, "https://%s.blob.core.windows.net", storageAccountName);
  }

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_STORAGE_ACCOUNT;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("storageAccountName", getStorageAccountName()));
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
        new GetAzureStorageStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);
    flight.addStep(
        new CreateAzureStorageStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getStorageAccountKeyProvider()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureStorageStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureStorageAttributes toApiAttributes() {
    return new ApiAzureStorageAttributes()
        .storageAccountName(getStorageAccountName())
        .region(getRegion());
  }

  public ApiAzureStorageResource toApiResource() {
    return new ApiAzureStorageResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureStorageAttributes(getStorageAccountName(), getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureStorage(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT
        || getResourceFamily() != WsmResourceFamily.AZURE_STORAGE_ACCOUNT
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected CONTROLLED_AZURE_STORAGE_ACCOUNT");
    }
    if (getStorageAccountName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required storage account name field for ControlledAzureStorage.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureStorage.");
    }
    ResourceValidationUtils.validateStorageAccountName(getStorageAccountName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureStorageResource that = (ControlledAzureStorageResource) o;

    return storageAccountName.equals(that.getStorageAccountName());
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAzureStorageResource that = (ControlledAzureStorageResource) o;

    return storageAccountName.equals(that.getStorageAccountName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 42 * result + storageAccountName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String storageAccountName;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureStorageResource.Builder storageAccountName(String storageAccountName) {
      this.storageAccountName = storageAccountName;
      return this;
    }

    public ControlledAzureStorageResource build() {
      return new ControlledAzureStorageResource(common, storageAccountName);
    }
  }
}
