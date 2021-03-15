package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

public class CloudSyncRoleMapping {
  public static final ImmutableMap<WsmIamRole, List<String>> cloudSyncRoleMap =
      ImmutableMap.of(
          WsmIamRole.OWNER,
              ImmutableList.of(
                  "roles/viewer",
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
                  "roles/storage.admin"),
          WsmIamRole.WRITER,
              ImmutableList.of(
                  "roles/viewer",
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
                  "roles/storage.admin"),
          WsmIamRole.READER, ImmutableList.of("roles/viewer"));
}
