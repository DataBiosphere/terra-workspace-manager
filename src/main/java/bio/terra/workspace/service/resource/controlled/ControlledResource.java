package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  private final String owner;

  public ControlledResource(
      String resourceName,
      CloningInstructions cloningInstructions,
      String description,
      UUID workspaceId,
      String owner) {
    super(resourceName, cloningInstructions, description, workspaceId);
    this.owner = owner;
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
  }

  public Optional<String> getOwner() {
    return Optional.ofNullable(owner);
  }

  @Override
  public void validate() {
    super.validate();

    if (getResourceType() == null || getJsonAttributes() == null) {
      throw new IllegalStateException("Missing required field for ControlledResource.");
    }
  }

  /**
   * Generate a model suitable for serialization into the workspace_resource table, via the
   * ControlledResourceDao.
   *
   * @return model to be saved in the database.
   */
  public ControlledResourceDbModel toResourceDbModel(UUID resourceId) {
    return ControlledResourceDbModel.builder()
        .setResourceId(resourceId)
        .setWorkspaceId(getWorkspaceId())
        .setOwner(getOwner().orElse(null))
        .setAttributes(getJsonAttributes())
        .build();
  }

  /** Build a request for the data reference dao to store that portion of the thing. */
  public DataReferenceRequest toDataReferenceRequest(UUID resourceId) {
    return DataReferenceRequest.builder()
        .workspaceId(getWorkspaceId())
        .name(getName())
        .description(getDescription())
        .resourceId(resourceId)
        .cloningInstructions(getCloningInstructions())
        .referenceType(getResourceType())
        .referenceObject(getReferenceObject())
        .build();
  }

  public abstract WsmResourceType getResourceType();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ControlledResource)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ControlledResource that = (ControlledResource) o;
    return Objects.equals(getOwner(), that.getOwner());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getOwner());
  }
}
