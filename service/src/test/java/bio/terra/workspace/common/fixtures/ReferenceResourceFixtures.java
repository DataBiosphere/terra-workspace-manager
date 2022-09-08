package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import java.util.Map;
import java.util.UUID;

public class ReferenceResourceFixtures {
  public static ReferencedDataRepoSnapshotResource makeDataRepoSnapshotResource(
      UUID workspaceUuid) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + resourceId.toString();

    return new ReferencedDataRepoSnapshotResource(
        workspaceUuid,
        resourceId,
        resourceName,
        "description of " + resourceName,
        CloningInstructions.COPY_NOTHING,
        "terra",
        "polaroid",
        /*resourceLineage=*/ null,
        /*properties*/ Map.of());
  }
}
