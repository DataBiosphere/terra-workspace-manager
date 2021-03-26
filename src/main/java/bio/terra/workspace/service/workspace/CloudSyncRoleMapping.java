package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

public class CloudSyncRoleMapping {
  private static final List<String> READER_PERMISSIONS = ImmutableList.of("roles/viewer");
  private static final List<String> WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(READER_PERMISSIONS)
          .add(
              "roles/bigquery.dataEditor",
              // TODO(wchambers): Revise service account permissions when there are controlled
              // resources for service accounts. (Also used by NextFlow)
              "roles/iam.serviceAccountUser",
              "roles/lifesciences.editor",
              "roles/serviceusage.serviceUsageConsumer",
              // TODO(wchambers): Revise notebooks permissions when there are controlled
              // resources for notebooks.
              "roles/notebooks.admin",
              // TODO(marikomedlock): Revise storage permissions when there are controlled
              // resources for buckets.
              "roles/storage.admin")
          .build();
  // Currently, workspace editors, applications and owners have the sam cloud permissions as
  // writers. If that changes, create a new list and modify the map below.
  public static final ImmutableMap<WsmIamRole, List<String>> CLOUD_SYNC_ROLE_MAP =
      ImmutableMap.of(
          // TODO: this should map to OWNER_PERMISSIONS if that's created.
          WsmIamRole.OWNER, WRITER_PERMISSIONS,
          // TODO: this should map to APPLICATION_PERMISSIONS if that's created.
          WsmIamRole.APPLICATION, WRITER_PERMISSIONS,
          WsmIamRole.WRITER, WRITER_PERMISSIONS,
          WsmIamRole.READER, READER_PERMISSIONS);
}
