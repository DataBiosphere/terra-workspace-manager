package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceAttributes;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ControlledFlexibleResource extends ControlledResource {
  private final String typeNamespace;
  private final String type;
  private final String data;

  @JsonCreator
  public ControlledFlexibleResource(
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
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region,
      @JsonProperty("typeNamespace") String typeNamespace,
      @JsonProperty("type") String type,
      @JsonProperty("data") String data) {
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
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
    validate();
  }

  /*
     // TODO: PF-2512 remove constructor above and enable this constructor
   @JsonCreator
   public FlexibleResourceResource(
       @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
       @JsonProperty("wsmControlledResourceFields")
           WsmControlledResourceFields controlledResourceFields,
       @JsonProperty("type") String type,
       @JsonProperty("typeNamespace") String typeNamespace,
       @JsonProperty("data") Object data)
  {
     super(resourceFields, controlledResourceFields);
     this.typeNamespace = typeNamespace;
     this.type = type;
     this.data = data;
     validate();
   }
    */

  // Constructor for the builder
  private ControlledFlexibleResource(
      ControlledResourceFields common, String typeNamespace, String type, String data) {
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

  public String getData() {
    return data;
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

    // Allow file sizes of up to X kilobytes (?)
    //    if (getData().length > MAX_FLEXIBLE_RESOURCE_DATA_BYTE_SIZE) {
    //      throw new MissingRequiredFieldException(
    //          "Field data is too large. Please limit it to X size.");
    //    }
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
    private String data;

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

    public ControlledFlexibleResource.Builder data(String data) {
      this.data = data;
      return this;
    }

    public ControlledFlexibleResource build() {
      return new ControlledFlexibleResource(common, typeNamespace, type, data);
    }
  }
}
