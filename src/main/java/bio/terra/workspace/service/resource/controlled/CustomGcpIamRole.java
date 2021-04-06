package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import java.util.List;

/**
 * A CustomGcpIamRole is a POJO value class representing a GCP custom IAM role. These GCP custom
 * roles are used to narrowly scope the sets of cloud permissions we grant to users.
 *
 * <p>Each custom role is the application of a resource-level IAM role to a specific resource type.
 * A CustomRole object holds a name made by combining the IAM role and resource type, and also holds
 * a set of GCP cloud permissions that are granted. See the full list of these permissions at
 * https://cloud.google.com/iam/docs/permissions-reference
 *
 * <p>The role name is the combination of resource type + IAM role name with an underscore
 * separator, e.g. GCS_BUCKET_READER.
 */
public class CustomGcpIamRole {
  private final WsmResourceType resourceType;
  private final ControlledResourceIamRole iamRole;
  private final List<String> includedPermissions;

  public CustomGcpIamRole(
      WsmResourceType resourceType,
      ControlledResourceIamRole iamRole,
      List<String> includedPermissions) {
    this.resourceType = resourceType;
    this.iamRole = iamRole;
    this.includedPermissions = includedPermissions;
  }

  /**
   * Builds the name of a custom GCP IAM role from the resource type and resource-level IAM
   * combination it applies to.
   */
  public static String customGcpRoleName(
      WsmResourceType resourceType, ControlledResourceIamRole iamRole) {
    return resourceType.name().toUpperCase() + "_" + iamRole.name().toUpperCase();
  }

  public String getRoleName() {
    return customGcpRoleName(resourceType, iamRole);
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
