package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import java.util.List;

/**
 * A CustomRole is a POJO value class representing a GCP custom IAM role.
 *
 * <p>Each custom role is the application of a workspace IAM role to a specific resource type. A
 * CustomRole object holds a name made by combining the IAM role and resource type, and also holds a
 * set of GCP cloud permissions that are granted. See the full list of these permissions at
 * https://cloud.google.com/iam/docs/permissions-reference
 *
 * <p>The role name is the combination of resource type + ApiIamRole name with an underscore
 * separator, e.g. GCS_BUCKET_READER.
 */
public class CustomGcpIamRole {
  private final WsmResourceType resourceType;
  private final WsmIamRole iamRole;
  private final List<String> includedPermissions;

  public CustomGcpIamRole(
      WsmResourceType resourceType, WsmIamRole iamRole, List<String> includedPermissions) {
    this.resourceType = resourceType;
    this.iamRole = iamRole;
    this.includedPermissions = includedPermissions;
  }

  /**
   * Builds the name of a custom GCP IAM role from the resource type and workspace-level IAM
   * combination it applies to.
   */
  public static String customGcpRoleName(WsmResourceType resourceType, WsmIamRole iamRole) {
    return resourceType.name() + "_" + iamRole.name();
  }

  public String getRoleName() {
    return customGcpRoleName(resourceType, iamRole);
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
