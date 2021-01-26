package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.IamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

public class CloudSyncRoleMapping {
  public static final ImmutableMap<IamRole, List<String>> cloudSyncRoleMap =
      ImmutableMap.of(
          IamRole.OWNER,
              ImmutableList.of(
                  "roles/viewer",
                  "roles/bigquery.dataEditor",
                  "roles/lifesciences.editor",
                  // TODO(wchambers): Revise notebooks permissions when there are controlled resources for notebooks.
                  "roles/notebooks.admin",
                  "roles/storage.objectAdmin"),
          IamRole.WRITER,
              ImmutableList.of(
                  "roles/viewer",
                  "roles/bigquery.dataEditor",
                  "roles/lifesciences.editor",
                  // TODO(wchambers): Revise notebooks permissions when there are controlled resources for notebooks.
                  "roles/notebooks.admin",
                  "roles/storage.objectAdmin"),
          IamRole.READER, ImmutableList.of("roles/viewer"));
}
