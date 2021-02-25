package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import java.util.List;

/**
 * A CustomRole is a POJO value class representing a GCP custom IAM role.
 *
 * <p>Each custom role is the application of a workspace IAM role to a specific resource type. A
 * CustomRole object holds a name made by combining the IAM role and resource type, and also holds a
 * list of GCP cloud permissions that are granted.
 *
 * <p>The role name is the combination of resource type + IamRole name with an underscore separator,
 * e.g. GCS_BUCKET_READER.
 */
public class CustomGcpIamRole {
  private String roleName;
  private List<String> includedPermissions;

  public CustomGcpIamRole(
      WsmResourceType resourceType, IamRole iamRole, List<String> includedPermissions) {
    this.roleName = resourceType.name() + "_" + iamRole.name();
    this.includedPermissions = includedPermissions;
  }

  public String getRoleName() {
    return roleName;
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
