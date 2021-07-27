package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

/**
 * This mapping describes the project-level GCP roles granted to members of a workspace.
 *
 * <p>Granting these roles at the project level was implemented as a temporary workaround to support
 * objects in a cloud context before controlled resources were built. As controlled resources become
 * available, roles should be granted directly on controlled resources instead (see {@code
 * CustomGcpIamRoleMapping}), and should be removed from this list. Some permissions must be granted
 * at the project level, and will continue to live here.
 */
public class CloudSyncRoleMapping {

  // Note that custom roles defined at the project level cannot contain the
  // "resourcemanager.projects.list" permission, even though it was previously included here.
  // See https://cloud.google.com/iam/docs/understanding-custom-roles#known_limitations
  private static final List<String> PROJECT_READER_PERMISSIONS =
      ImmutableList.of(
          "bigquery.jobs.create",
          "bigquery.readsessions.create",
          "bigquery.readsessions.getData",
          "bigquery.readsessions.update",
          "lifesciences.operations.get",
          "lifesciences.operations.list",
          "monitoring.timeSeries.list",
          "resourcemanager.projects.get",
          "serviceusage.operations.get",
          "serviceusage.operations.list",
          "serviceusage.quotas.get",
          "serviceusage.services.get",
          "serviceusage.services.list");
  private static final List<String> PROJECT_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(PROJECT_READER_PERMISSIONS)
          .add(
              // TODO(wchambers): Revise service account permissions when there are controlled
              // resources for service accounts. (Also used by NextFlow)
              "iam.serviceAccounts.actAs",
              "iam.serviceAccounts.get",
              "iam.serviceAccounts.list",
              "lifesciences.operations.cancel",
              "lifesciences.workflows.run",
              "notebooks.operations.cancel",
              "notebooks.operations.delete",
              "notebooks.operations.get",
              "notebooks.operations.list",
              "serviceusage.services.use")
          .build();

  private static final CustomGcpIamRole PROJECT_READER =
      CustomGcpIamRole.of("PROJECT_READER", PROJECT_READER_PERMISSIONS);
  private static final CustomGcpIamRole PROJECT_WRITER =
      CustomGcpIamRole.of("PROJECT_WRITER", PROJECT_WRITER_PERMISSIONS);
  // Currently, workspace editors, applications and owners have the same cloud permissions as
  // writers. If that changes, create a new CustomGcpIamRole and modify the map below.
  public static final ImmutableMap<WsmIamRole, CustomGcpIamRole> CUSTOM_GCP_PROJECT_IAM_ROLES =
      ImmutableMap.of(
          // TODO: this should map to PROJECT_OWNER if that's created.
          WsmIamRole.OWNER, PROJECT_WRITER,
          // TODO: this should map to PROJECT_APPLICATION if that's created.
          WsmIamRole.APPLICATION, PROJECT_WRITER,
          WsmIamRole.WRITER, PROJECT_WRITER,
          WsmIamRole.READER, PROJECT_READER);
}
