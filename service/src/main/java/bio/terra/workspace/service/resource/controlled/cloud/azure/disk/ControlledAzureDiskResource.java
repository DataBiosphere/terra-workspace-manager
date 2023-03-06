package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureDiskResource extends ControlledResource {
  private final String diskName;

  /** size is in GB */
  private final int size;

  @JsonCreator
  public ControlledAzureDiskResource(
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
      @JsonProperty("diskName") String diskName,
      @JsonProperty("region") String region,
      @JsonProperty("size") int size,
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

    this.diskName = diskName;
    this.size = size;
    validate();
  }

  /*
    // TODO: PF-2512 remove constructor above and enable this constructor
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
  */

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

  public String getDiskName() {
    return diskName;
  }

  public int getSize() {
    return size;
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
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureDiskStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {}

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
    ResourceValidationUtils.validateAzureRegion(getRegion());
    ResourceValidationUtils.validateAzureDiskName(getDiskName());
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
