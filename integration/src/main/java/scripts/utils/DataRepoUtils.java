package scripts.utils;

import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.model.DataRepoSnapshotAttributes;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.UpdateDataRepoSnapshotReferenceRequestBody;
import java.util.UUID;
import javax.annotation.Nullable;

public class DataRepoUtils {

  /** Calls WSM to create a referenced TDR snapshot in the specified workspace. */
  public static DataRepoSnapshotResource makeDataRepoSnapshotReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      String dataRepoSnapshotId,
      String dataRepoInstanceName)
      throws ApiException {

    var body =
        new CreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .snapshot(
                new DataRepoSnapshotAttributes()
                    .snapshot(dataRepoSnapshotId)
                    .instanceName(dataRepoInstanceName));

    return resourceApi.createDataRepoSnapshotReference(body, workspaceId);
  }

  /** Updates name, description, and/or referencing target of a data repo snapshot reference. */
  public static void updateDataRepoSnapshotReferenceResource(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String instanceId,
      @Nullable String snapshot)
      throws ApiException {
    UpdateDataRepoSnapshotReferenceRequestBody body =
        new UpdateDataRepoSnapshotReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (instanceId != null) {
      body.setInstanceName(instanceId);
    }
    if (snapshot != null) {
      body.setSnapshot(snapshot);
    }
    resourceApi.updateDataRepoSnapshotReferenceResource(body, workspaceId, resourceId);
  }
}
