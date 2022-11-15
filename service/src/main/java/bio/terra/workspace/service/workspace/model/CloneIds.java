package bio.terra.workspace.service.workspace.model;

import java.util.Map;
import java.util.UUID;

/**
 * When we clone a workspace, we need to pre-generate the ids for the destination resources and the
 * destination folders. That way they can be constant inputs to the clone flight.
 */
public record CloneIds(
    Map<UUID, UUID> folderIdMap,
    Map<UUID, UUID> referencedResourceIdMap,
    Map<UUID, UUID> controlledResourceIdMap) {}
