package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.db.model.DbResource;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class contains the common controlled resource fields. It is used to serialize common fields
 * to JSON and deserialize from JSON to the common fields.
 *
 * <p>The {@link ControlledResourceFields} class is the dynamic, in-memory class used to build and
 * process the fields. It contains an instance of this object.
 */
public record WsmControlledResourceFields(
    @Nullable String assignedUser,
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable PrivateResourceState privateResourceState,
    AccessScopeType accessScope,
    ManagedByType managedBy,
    @Nullable String applicationId,
    @Nullable String region,
    @Nullable List<String> userAssignedIdentities) {

  public static WsmControlledResourceFields fromDb(DbResource dbResource) {
    return new WsmControlledResourceFields(
        dbResource.getAssignedUser().orElse(null),
        dbResource.getPrivateResourceState().orElse(null),
        dbResource.getAccessScope(),
        dbResource.getManagedBy(),
        dbResource.getApplicationId().orElse(null),
        dbResource.getRegion(),
        null // TODO add userAssignedIdentities to DB
        );
  }

  public static WsmControlledResourceFields fromControlledResource(
      ControlledResource controlledResource) {
    return new WsmControlledResourceFields(
        controlledResource.getAssignedUser().orElse(null),
        controlledResource.getPrivateResourceState().orElse(null),
        controlledResource.getAccessScope(),
        controlledResource.getManagedBy(),
        controlledResource.getApplicationId(),
        controlledResource.getRegion(),
        null // TODO add userAssignedIdentities to DB
        );
  }
}
