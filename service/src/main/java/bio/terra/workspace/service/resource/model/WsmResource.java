package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import com.google.common.base.Strings;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Top-level class for a Resource. Children of this class can be controlled resources, references,
 * or (future) monitored resources.
 */
public abstract class WsmResource {
  private final UUID workspaceUuid;
  private final UUID resourceId;
  private final String name;
  private @Nullable final String description;
  private final CloningInstructions cloningInstructions;
  private @Nullable final List<ResourceLineageEntry> resourceLineage;

  /**
   * construct from individual fields
   *
   * @param workspaceUuid unique identifier of the workspace where this resource lives (or is going
   *     to live)
   * @param resourceId unique identifier of the resource
   * @param name resource name; unique within a workspace
   * @param description free-form text description of the resource
   * @param cloningInstructions how to treat the resource when cloning the workspace
   * @param resourceLineage resource lineage
   */
  public WsmResource(
      UUID workspaceUuid,
      UUID resourceId,
      String name,
      @Nullable String description,
      CloningInstructions cloningInstructions,
      List<ResourceLineageEntry> resourceLineage) {
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
    this.name = name;
    this.description = description;
    this.cloningInstructions = cloningInstructions;
    this.resourceLineage = resourceLineage;
  }

  /** construct from database data */
  public WsmResource(DbResource dbResource) {
    this(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName(),
        dbResource.getDescription(),
        dbResource.getCloningInstructions(),
        dbResource.getResourceLineage().orElse(null));
  }

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return resourceLineage;
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
   * Each resource is able to create the API union object to return resources
   *
   * @return resource union with the proper resource filled in
   */
  public abstract ApiResourceUnion toApiResourceUnion();

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
  public abstract <T> T castByEnum(WsmResourceType expectedType);

  /**
   * The API metadata object contains the data for both referenced and controlled resources. This
   * class fills in the common part. Referenced resources have no additional data to fill in.
   * Controlled resources overrides this method to fill in the controlled resource specifics.
   *
   * @return partially constructed Api Model common resource description
   */
  public ApiResourceMetadata toApiMetadata() {
    ApiResourceMetadata apiResourceMetadata =
        new ApiResourceMetadata()
            .workspaceId(workspaceUuid)
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .resourceType(getResourceType().toApiModel())
            .stewardshipType(getStewardshipType().toApiModel())
            .cloudPlatform(getResourceType().getCloudPlatform().toApiModel())
            .cloningInstructions(cloningInstructions.toApiModel());
    if (resourceLineage != null) {
      ApiResourceLineage apiResourceLineage = new ApiResourceLineage();
      apiResourceLineage.addAll(
          resourceLineage.stream().map(ResourceLineageEntry::toApiModel).toList());
      apiResourceMetadata.resourceLineage(apiResourceLineage);
    }
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
        || getResourceId() == null) {
      throw new MissingRequiredFieldException("Missing required field for WsmResource.");
    }
    ResourceValidationUtils.validateResourceName(getName());
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WsmResource that = (WsmResource) o;

    if (workspaceUuid != null
        ? !workspaceUuid.equals(that.workspaceUuid)
        : that.workspaceUuid != null) return false;
    if (resourceId != null ? !resourceId.equals(that.resourceId) : that.resourceId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    return cloningInstructions == that.cloningInstructions;
  }

  @Override
  public int hashCode() {
    int result = workspaceUuid != null ? workspaceUuid.hashCode() : 0;
    result = 31 * result + (resourceId != null ? resourceId.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (cloningInstructions != null ? cloningInstructions.hashCode() : 0);
    return result;
  }
}
