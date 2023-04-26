package bio.terra.workspace.service.workspace.flight.removeuser;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A pair of ControlledResource and ControlledResourceIamRole objects. This represents a single role
 * on a particular controlled resource.
 */
public class ResourceRolePair {

  private final ControlledResource resource;
  private final ControlledResourceIamRole role;

  @JsonCreator
  public ResourceRolePair(
      @JsonProperty("resource") ControlledResource resource,
      @JsonProperty("role") ControlledResourceIamRole role) {
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
