package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

/**
 * This mapping describes the project-level GCP roles granted to members of a workspace.
 *
 * <p>Granting these roles at the project level was implemented as a temporary workaround to support
 * objects in a cloud context before controlled resources were built. As controlled resources become
 * available, roles should be granted directly on controlled resources instead (see {@code
 * CustomGcpIamRoleMapping}), and should be removed from this list.
 */
public class CloudSyncRoleMapping {
  // Note that bigquery.jobUser is required at the project level, unlike other permissions which can
  // be per-dataset.
  private static final List<String> READER_PERMISSIONS =
      ImmutableList.of(
          "roles/bigquery.jobUser",
          "roles/lifesciences.viewer",
          "roles/serviceusage.serviceUsageViewer");
  private static final List<String> WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(READER_PERMISSIONS)
          .add(
              // TODO(wchambers): Revise service account permissions when there are controlled
              // resources for service accounts. (Also used by NextFlow)
              "roles/iam.serviceAccountUser",
              "roles/lifesciences.editor",
              "roles/serviceusage.serviceUsageConsumer")
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
