package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;

public record ClonedCopyNothingResource(
    CloningInstructions effectiveCloningInstructions, UUID sourceWorkspaceId, UUID sourceResourceId)
    implements ClonedAzureResource<ControlledResource> {}
