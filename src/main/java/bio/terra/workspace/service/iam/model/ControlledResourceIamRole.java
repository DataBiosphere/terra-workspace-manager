package bio.terra.workspace.service.iam.model;

/**
 * Internal representation of resource-level IAM roles. See {@Code
 * ControlledResourceInheritanceMapping} for the canonical mapping of workspace IamRoles to
 * equivalent ControlledResourceIamRoles.
 */
public enum ControlledResourceIamRole {
  OWNER("owner"),
  // Only private resources have the ASSIGNER role defined in Sam.
  ASSIGNER("assigner"),
  EDITOR("editor"),
  WRITER("writer"),
  READER("reader");

  private final String samRole;

  ControlledResourceIamRole(String samRole) {
    this.samRole = samRole;
  }

  public String toSamRole() {
    return samRole;
  }
}
