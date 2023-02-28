package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for building a GCP policy object. Use this builder as the "modify" step of the
 * read-modify-write IAM pattern for GCP objects in order to grant workspace users appropriate
 * permissions.
 */
public class GcpPolicyBuilder {

  private final ControlledResource resource;
  private final String projectId;
  private final Policy currentPolicy;
  private final List<Binding> bindings;

  public GcpPolicyBuilder(ControlledResource resource, String projectId, Policy currentPolicy) {
    this.resource = resource;
    this.projectId = projectId;
    this.currentPolicy = currentPolicy;
    this.bindings = new ArrayList<>();
    bindings.addAll(currentPolicy.getBindingsList());
  }

  public GcpPolicyBuilder addResourceBinding(ControlledResourceIamRole role, String member) {
    bindings.add(buildBinding(role, member));
    return this;
  }

  public Policy build() {
    return Policy.newBuilder()
        .setVersion(currentPolicy.getVersion())
        .setBindings(bindings)
        .setEtag(currentPolicy.getEtag())
        .build();
  }

  /**
   * Build a Binding object granting a custom GCP role on a resource to a single member.
   *
   * @param resourceRole The role being granted on a resource
   * @param memberIdentifier The member being granted a role. This should be a member as specified
   *     by GCP, e.g. groups should be prefixed with the literal 'group:'.
   * @return Binding object granting a custom GCP role to provided user.
   */
  private Binding buildBinding(ControlledResourceIamRole resourceRole, String memberIdentifier) {
    String customRole = getCustomRole(resourceRole);
    return Binding.newBuilder()
        .setRole(customRole)
        .setMembers(Collections.singletonList(memberIdentifier))
        .build();
  }

  public String getCustomRole(ControlledResourceIamRole resourceRole) {
    CustomGcpIamRole customRole =
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.get(
            resource.getResourceType(), resourceRole);
    if (customRole == null) {
      throw new InternalLogicException(
          String.format("Missing custom GCP resource role %s", resourceRole));
    }
    return customRole.getFullyQualifiedRoleName(projectId);
  }
}
