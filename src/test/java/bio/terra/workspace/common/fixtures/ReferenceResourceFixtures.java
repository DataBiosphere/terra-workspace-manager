package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.reference.ReferenceDataRepoSnapshotResource;
import java.util.UUID;

public class ReferenceResourceFixtures {
  public static ReferenceDataRepoSnapshotResource makeDataRepoSnapshotResource(UUID workspaceId) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + resourceId.toString();

    return new ReferenceDataRepoSnapshotResource(
        workspaceId,
        resourceId,
        resourceName,
        "description of " + resourceName,
        CloningInstructions.COPY_NOTHING,
        "terra",
        "polaroid");
  }
}
