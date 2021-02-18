package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import java.util.UUID;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  public ControlledResource(
      String resourceName,
      CloningInstructions cloningInstructions,
      String description,
      UUID workspaceId,
      String owner) {
    super(resourceName, cloningInstructions, description, workspaceId, owner);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED_RESOURCE;
  }

  public abstract CloudPlatform getCloudPlatform();

  public abstract WsmResourceType getResourceType();

  /**
   * Generate a model suitable for serialization into the workspace_resource table, via the
   * ControlledResourceDao. Note that this method should not be called before the resource ID has
   * been created and set, as it is the primary key for this table.
   *
   * @return model to be saved in the database.
   */
  public ControlledResourceDbModel toResourceDbModel(UUID resourceId) {
    return ControlledResourceDbModel.builder()
        .setResourceId(resourceId)
        .setWorkspaceId(getWorkspaceId())
        .setOwner(getOwner())
        .setAttributes(getJsonAttributes())
        .build();
  }

  /**
   * Build a request for the data reference dao to store that portion of the thing.
   *
   * @return
   */
  public DataReferenceRequest toDataReferenceRequest(UUID resourceId) {
    return DataReferenceRequest.builder()
        .workspaceId(getWorkspaceId())
        .name(getName())
        .referenceDescription(getDescription())
        .resourceId(resourceId)
        .cloningInstructions(getCloningInstructions())
        .referenceType(getResourceType().toDataReferenceType())
        .referenceObject(getReferenceObject())
        .build();
  }
  /**
   * Attributes string, serialized as JSON. Includes only those attributes of the cloud resource
   * that are necessary for identification.
   *
   * @return json string
   */
  public abstract String getJsonAttributes();

  /**
   * Provide something to satisfy the requiremenet of the reference object column in the
   * workspace_data_reference table. TODO: can we get rid of this?
   *
   * @return
   */
  public abstract ReferenceObject getReferenceObject();
}
