package bio.terra.workspace.service.resource.model;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_NOTHING;
import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_REFERENCE;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.CloneInstructionNotSupportedException;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Top-level class for a Resource. Children of this class can be controlled resources, references,
 * or (future) monitored resources.
 */
public abstract class WsmResource {
  private final WsmResourceFields wsmResourceFields;

  /**
   * Base resource constructor
   *
   * @param wsmResourceFields common resource fields
   */
  @JsonCreator
  public WsmResource(@JsonProperty("wsmResourceFields") WsmResourceFields wsmResourceFields) {
    this.wsmResourceFields = wsmResourceFields;
  }

  /** construct from database data */
  public WsmResource(DbResource dbResource) {
    this(new WsmResourceFields(dbResource));
  }

  // Getter used for serdes
  // The rest of the getters indirect through resourceFields
  public WsmResourceFields getWsmResourceFields() {
    return wsmResourceFields;
  }

  public UUID getWorkspaceId() {
    return wsmResourceFields.getWorkspaceId();
  }

  public UUID getResourceId() {
    return wsmResourceFields.getResourceId();
  }

  public String getName() {
    return wsmResourceFields.getName();
  }

  public @Nullable String getDescription() {
    return wsmResourceFields.getDescription();
  }

  public CloningInstructions getCloningInstructions() {
    return wsmResourceFields.getCloningInstructions();
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return wsmResourceFields.getResourceLineage();
  }

  public ImmutableMap<String, String> getProperties() {
    return wsmResourceFields.getProperties();
  }

  public String getCreatedByEmail() {
    return wsmResourceFields.getCreatedByEmail();
  }

  public @Nullable OffsetDateTime getCreatedDate() {
    return wsmResourceFields.getCreatedDate();
  }

  public @Nullable String getLastUpdatedByEmail() {
    return wsmResourceFields.getLastUpdatedByEmail();
  }

  public @Nullable OffsetDateTime getLastUpdatedDate() {
    return wsmResourceFields.getLastUpdatedDate();
  }

  public WsmResourceState getState() {
    return wsmResourceFields.getState();
  }

  public @Nullable String getFlightId() {
    return wsmResourceFields.getFlightId();
  }

  public @Nullable ErrorReportException getError() {
    return wsmResourceFields.getError();
  }

  /**
   * Sub-classes must identify their stewardship type
   *
   * @return stewardship type
   */
  public abstract StewardshipType getStewardshipType();

  /**
   * Sub-classes must identify their resource type
   *
   * @return resource type
   */
  public abstract WsmResourceType getResourceType();

  /**
   * Sub-classes must identify their resource family
   *
   * @return resource family
   */
  public abstract WsmResourceFamily getResourceFamily();

  /**
   * Attributes string, serialized as JSON. Includes only those attributes of the cloud resource
   * that are necessary for identification. The structure of the cloud resource attributes can be
   * whatever is useful for the resource. It need not be a flat POJO.
   *
   * @return json string
   */
  public abstract String attributesToJson();

  /**
   * Each resource is able to create the API union object to return resource attributes
   *
   * @return attributes union with the proper attribute filled in
   */
  public abstract ApiResourceAttributesUnion toApiAttributesUnion();

  /**
   * Every subclass mst implement this cast to its own type. This implementation should be made in
   * each subclass:
   *
   * <pre>{@code
   * @Override
   * @SuppressWarnings("unchecked")
   * public <T> T castByEnum(WsmResourceType expectedType) {
   *   if (getResourceType() != expectedType) {
   *     throw new BadRequestException(String.format("Resource is not a %s", expectedType));
   *   }
   *   return (T) this;
   * }
   * }</pre>
   *
   * @param expectedType resource type enum
   * @param <T> implicit
   * @return cast to subtype
   */
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  /**
   * The UpdateResourceFlight calls this method to populate the resource-specific step(s) to modify
   * resource attributes and/or cloud resources. We provide a default implementation for the "no
   * steps" case.
   *
   * @param flight The update flight
   * @param flightBeanBag Bean bag for finding Spring singletons.
   */
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  /**
   * Build a ReferencedResource clone object of this resource object. Both controlled and referenced
   * resources can be the source of a referenced in a cloned workspace. For referenced resources,
   * the clone is typically a copy of the reference. For controlled resources, the clone is a
   * reference to the resource in this workspace.
   *
   * <p>If name or description is not supplied, then we use the one from this resource.
   *
   * <p>This default implementation throws CloneInstructionNotSupportedException.
   *
   * @param destinationWorkspaceUuid id of the destination workspace
   * @param name optional resource name override
   * @param description optional resource description override
   * @return WsmResource that is the referenced resource object
   */
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    throw new CloneInstructionNotSupportedException(
        String.format(
            "You cannot make a reference clone of a %s resource", getResourceType().name()));
  }

  protected WsmResourceFields buildReferencedCloneResourceCommonFields(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {

    WsmResourceFields.Builder<?> cloneResourceCommonFields = getWsmResourceFields().toBuilder();

    // TODO: PF-2107 We should set cloning instructions to REFERENCE. When we move the prep
    //  earlier, we will not be considering resources that have instruction COPY_NOTHING.
    //  However, I am unsure if the current code behaves that way, so added this temporary
    //  code to copy COPY_NOTHING from the source.
    CloningInstructions cloningInstructions =
        (getCloningInstructions() != COPY_NOTHING ? COPY_REFERENCE : COPY_NOTHING);

    cloneResourceCommonFields
        .workspaceUuid(destinationWorkspaceUuid)
        .resourceId(destinationResourceId)
        .resourceLineage(buildCloneResourceLineage())
        .cloningInstructions(cloningInstructions)
        .properties(buildCloneProperties(destinationWorkspaceUuid, destinationFolderId))
        .createdByEmail(createdByEmail);

    // override name and description if provided
    if (name != null) {
      cloneResourceCommonFields.name(name);
    }
    if (description != null) {
      cloneResourceCommonFields.description(description);
    }
    return cloneResourceCommonFields.build();
  }

  protected List<ResourceLineageEntry> buildCloneResourceLineage() {
    List<ResourceLineageEntry> cloneResourceLineage =
        getResourceLineage() != null ? getResourceLineage() : new ArrayList<>();
    cloneResourceLineage.add(new ResourceLineageEntry(getWorkspaceId(), getResourceId()));
    return cloneResourceLineage;
  }

  protected Map<String, String> buildCloneProperties(
      UUID destinationWorkspaceId, @Nullable UUID destinationFolderId) {
    Map<String, String> destinationResourceProperties = new HashMap<>(getProperties());
    if (!destinationWorkspaceId.equals(getWorkspaceId())) {
      if (destinationFolderId != null) {
        // Cloning a work with folder ID to corresponding folder in destination workspace.
        destinationResourceProperties.put(FOLDER_ID_KEY, destinationFolderId.toString());
      } else {
        // We're cloning an individual resource. If folder ID property exists, clear it, since
        // folder doesn't exist in destination workspace.
        destinationResourceProperties.remove(FOLDER_ID_KEY);
      }
    }
    return ImmutableMap.copyOf(destinationResourceProperties);
  }

  /**
   * The API metadata object contains the data for both referenced and controlled resources. This
   * class fills in the common part. Referenced resources have no additional data to fill in.
   * Controlled resources overrides this method to fill in the controlled resource specifics.
   *
   * @return partially constructed Api Model common resource description
   */
  public ApiResourceMetadata toApiMetadata() {
    ApiProperties apiProperties = convertMapToApiProperties(getProperties());
    ApiResourceMetadata apiResourceMetadata =
        new ApiResourceMetadata()
            .workspaceId(getWorkspaceId())
            .resourceId(getResourceId())
            .name(getName())
            .description(getDescription())
            .resourceType(getResourceType().toApiModel())
            .stewardshipType(getStewardshipType().toApiModel())
            .cloudPlatform(getResourceType().getCloudPlatform().toApiModel())
            .cloningInstructions(getCloningInstructions().toApiModel())
            .properties(apiProperties)
            .createdBy(getCreatedByEmail())
            .createdDate(getCreatedDate())
            .lastUpdatedBy(Optional.ofNullable(getLastUpdatedByEmail()).orElse(getCreatedByEmail()))
            .lastUpdatedDate(Optional.ofNullable(getLastUpdatedDate()).orElse(getCreatedDate()))
            .state(getState().toApi())
            .errorReport(
                Optional.ofNullable(getError())
                    .map(ErrorReportUtils::buildApiErrorReport)
                    .orElse(null))
            .jobId(getFlightId());
    ApiResourceLineage apiResourceLineage = new ApiResourceLineage();
    apiResourceLineage.addAll(
        getResourceLineage().stream().map(ResourceLineageEntry::toApiModel).toList());
    apiResourceMetadata.resourceLineage(apiResourceLineage);

    return apiResourceMetadata;
  }

  /**
   * Validate the state of to this object. Subclasses should override this method, calling super()
   * first to validate parent class properties (even if those are abstract). This will prevent
   * different resource type concrete classes from repeating the same validation code.
   */
  public void validate() {
    if (Strings.isNullOrEmpty(getName())
        || getWorkspaceId() == null
        || getCloningInstructions() == null
        || getStewardshipType() == null
        || getResourceId() == null
        || getProperties() == null
        || getCreatedByEmail() == null) {
      throw new MissingRequiredFieldException("Missing required field for WsmResource.");
    }
    ResourceValidationUtils.validateResourceName(getName());
    ResourceValidationUtils.validateProperties(getProperties());
    if (getDescription() != null) {
      ResourceValidationUtils.validateResourceDescriptionName(getDescription());
    }
    ResourceValidationUtils.validateCloningInstructions(
        getStewardshipType(), getCloningInstructions());
  }

  public ReferencedResource castToReferencedResource() {
    if (getStewardshipType() != StewardshipType.REFERENCED) {
      throw new InvalidMetadataException("Resource is not a referenced resource");
    }
    return (ReferencedResource) this;
  }

  public ControlledResource castToControlledResource() {
    if (getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Resource is not a controlled resource");
    }
    return (ControlledResource) this;
  }

  public CloningInstructions computeCloningInstructions(
      @Nullable ApiCloningInstructionsEnum apiCloningInstructions) {
    return Optional.ofNullable(apiCloningInstructions)
        .map(CloningInstructions::fromApiModel)
        .orElse(getCloningInstructions());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WsmResource that)) return false;
    return Objects.equal(wsmResourceFields, that.wsmResourceFields);
  }

  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (!(o instanceof WsmResource that)) return false;
    return wsmResourceFields.partialEqual(that.wsmResourceFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(wsmResourceFields);
  }
}
