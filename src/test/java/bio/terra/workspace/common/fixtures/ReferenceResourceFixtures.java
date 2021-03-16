package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import java.util.UUID;

public class ReferenceResourceFixtures {
  public static ReferencedDataRepoSnapshotResource makeDataRepoSnapshotResource(UUID workspaceId) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + resourceId.toString();

    return new ReferencedDataRepoSnapshotResource(
        workspaceId,
        resourceId,
        resourceName,
        "description of " + resourceName,
        CloningInstructions.COPY_NOTHING,
        "terra",
        "polaroid");
  }
}
