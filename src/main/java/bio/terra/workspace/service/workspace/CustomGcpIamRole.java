package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import java.util.List;

/**
 * A CustomRole is a POJO value class representing a GCP custom IAM role.
 *
 * <p>Each custom role is the application of a workspace IAM role to a specific resource type. A
 * CustomRole object holds this IAM role, resource type, and a list of GCP cloud permissions that
 * are granted.
 */
public class CustomGcpIamRole {
  private WsmResourceType resourceType;
  private IamRole iamRole;
  private List<String> includedPermissions;

  public CustomGcpIamRole(
      WsmResourceType resourceType, IamRole iamRole, List<String> includedPermissions) {
    this.resourceType = resourceType;
    this.iamRole = iamRole;
    this.includedPermissions = includedPermissions;
  }

  public WsmResourceType getResourceType() {
    return resourceType;
  }

  public IamRole getIamRole() {
    return iamRole;
  }

  public List<String> getIncludedPermissions() {
    return includedPermissions;
  }
}
