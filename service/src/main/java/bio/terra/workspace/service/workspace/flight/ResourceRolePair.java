package bio.terra.workspace.service.workspace.flight;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;

/**
 * A pair of ControlledResource and ControlledResourceIamRole objects. This represents a single role
 * on a particular controlled resource.
 */
public class ResourceRolePair {

  private final ControlledResource resource;
  private final ControlledResourceIamRole role;

  public ResourceRolePair(ControlledResource resource, ControlledResourceIamRole role) {
    this.resource = resource;
    this.role = role;
  }

  public ControlledResource getResource() {
    return resource;
  }

  public ControlledResourceIamRole getRole() {
    return role;
  }
}
