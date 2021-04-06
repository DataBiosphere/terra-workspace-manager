package bio.terra.workspace.service.resource.controlled.mappings;

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

  CustomGcpIamRole(
      WsmResourceType resourceType,
      ControlledResourceIamRole iamRole,
      List<String> includedPermissions) {
    this.resourceType = resourceType;
    this.iamRole = iamRole;
    this.includedPermissions = includedPermissions;
  }

  /**
   * Our custom role names are a combination of resource type and IAM role name, e.g.
   * GCS_BUCKET_READER. This is an arbitrary standard designed to be human-readable.
   */
  public String getRoleName() {
    return resourceType.name().toUpperCase() + "_" + iamRole.name().toUpperCase();
  }

  /**
   * GCP custom roles defined on a project have a fully qualified name which includes the project id
   * and role name. This is a convenience for formatting those values correctly.
   */
  public String getFullyQualifiedRoleName(String projectId) {
    return String.format("projects/%s/roles/%s", projectId, getRoleName());
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
