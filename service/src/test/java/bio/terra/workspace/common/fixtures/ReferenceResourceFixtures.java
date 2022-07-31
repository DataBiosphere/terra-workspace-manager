package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
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
        "polaroid");
  }

  public static ReferencedBigQueryDatasetResource makeReferencedBqDatasetResource(
      UUID workspaceId, String projectId, String bqDataset) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testbq-" + resourceId.toString();
    return new ReferencedBigQueryDatasetResource(
        workspaceId,
        resourceId,
        resourceName,
        "a description",
        CloningInstructions.COPY_NOTHING,
        projectId,
        bqDataset);
  }
}
