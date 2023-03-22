package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import java.util.List;

/**
 * A CustomGcpIamRole is a POJO value class representing a GCP custom IAM role. These GCP custom
 * roles are used to narrowly scope the sets of cloud permissions we grant to users.
 *
 * <p>Each custom role built by WSM is the application of a resource-level IAM role to a specific
 * resource type. For example, the GCS_BUCKET_READER custom role holds the permissions granted to
 * resource-level READERs on GCS buckets.
 *
 * <p>A CustomGcpIamRole object holds a name made by combining the IAM role and resource type, and
 * also holds a set of GCP cloud permissions that are granted. See the full list of these
 * permissions at https://cloud.google.com/iam/docs/permissions-reference
 *
 * <p>The role name is the combination of resource type + IAM role name with an underscore
 * separator, e.g. GCS_BUCKET_READER.
 */
public class CustomGcpIamRole {
  private final String roleName;
  private final List<String> includedPermissions;

  private CustomGcpIamRole(String roleName, List<String> includedPermissions) {
    this.roleName = roleName;
    this.includedPermissions = includedPermissions;
  }

  /**
   * Create a custom GCP role named by a combination of a resource type and resource-level IAM role.
   * Custom roles applied at the resource level should use this to generate the correct role name.
   *
   * @param cloudResourceType The type of cloud resource this role is intended for. Used for name
   *     generation
   * @param iamRole The IAM role this GCP role is intended for. Used for name generation.
   * @param includedPermissions The list of GCP permissions this role should grant.
   */
  public static CustomGcpIamRole ofResource(
      WsmResourceFamily cloudResourceType,
      ControlledResourceIamRole iamRole,
      List<String> includedPermissions) {
    String roleName = cloudResourceType.name().toUpperCase() + "_" + iamRole.name().toUpperCase();
    return new CustomGcpIamRole(roleName, includedPermissions);
  }

  /**
   * Create a custom GCP role from name and permissions list. Roles applied at the resource level
   * should use {@link CustomGcpIamRole#ofResource(WsmResourceFamily, ControlledResourceIamRole,
   * List)} instead.
   *
   * @param roleName roleName
   * @param includedPermissions includedPermissions
   */
  public static CustomGcpIamRole of(String roleName, List<String> includedPermissions) {
    return new CustomGcpIamRole(roleName, includedPermissions);
  }

  /**
   * Our custom role names are a combination of resource type and IAM role name, e.g.
   * GCS_BUCKET_READER. This is an arbitrary standard designed to be human-readable.
   */
  public String getRoleName() {
    return roleName;
  }

  /**
   * GCP custom roles defined on a project have a fully qualified name which includes the project id
   * and role name. This is a convenience for formatting those values correctly.
   */
  public String getFullyQualifiedRoleName(String projectId) {
    return String.format("projects/%s/roles/%s", projectId, roleName);
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
