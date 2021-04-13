package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRoleMapping;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for building a GCP policy object. Use this builder as the "modify" step of the
 * read-modify-write IAM pattern for GCP objects in order to grant workspace users appropriate
 * permissions.
 */
public class GcpPolicyBuilder {

  private final ControlledResource resource;
  private final String projectId;
  private final Policy currentPolicy;
  private List<Binding> bindings;

  public GcpPolicyBuilder(ControlledResource resource, String projectId, Policy currentPolicy) {
    this.resource = resource;
    this.projectId = projectId;
    this.currentPolicy = currentPolicy;
    this.bindings = new ArrayList<>();
    bindings.addAll(currentPolicy.getBindingsList());
  }

  public GcpPolicyBuilder addWorkspaceBinding(WsmIamRole role, String email) {
    bindings.addAll(bindingsForWorkspaceRole(resource, role, toMemberIdentifier(email), projectId));
    return this;
  }

  public GcpPolicyBuilder addResourceBinding(ControlledResourceIamRole role, String email) {
    bindings.add(
        buildBinding(resource.getResourceType(), role, projectId, toMemberIdentifier(email)));
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
   * GCP expects all groups to be prepended with the literal "group:" in IAM permissions bindings.
   */
  private static String toMemberIdentifier(String samGroupEmail) {
    return "group:" + samGroupEmail;
  }

  /**
   * Build a list of role bindings for a given workspace-level role on a controlled resource, using
   * ControlledResourceInheritanceMapping.
   *
   * @param resource The resource these bindings will apply to.
   * @param workspaceRole The workspace-level role granted to this user. Translated to GCP
   *     resource-specific roles using ControlledResourceInheritanceMapping.
   * @param group The group being granted a role. Should be prefixed with the literal "group:" for
   *     GCP.
   * @param projectId The GCP project ID
   */
  private static List<Binding> bindingsForWorkspaceRole(
      ControlledResource resource, WsmIamRole workspaceRole, String group, String projectId) {
    Multimap<WsmIamRole, ControlledResourceIamRole> roleInheritanceMap =
        resource.getCategory().getInheritanceMapping();
    Collection<ControlledResourceIamRole> resourceRoles = roleInheritanceMap.get(workspaceRole);
    return resourceRoles.stream()
        .map(
            resourceRole ->
                buildBinding(resource.getResourceType(), resourceRole, projectId, group))
        .collect(Collectors.toList());
  }

  /**
   * Convenience for building a Binding object granting a custom GCP role on a resource to a single
   * member.
   *
   * @param resourceType The type of resource this binding will apply to
   * @param resourceRole The role being granted on a resource
   * @param projectId ID of the GCP project
   * @param memberIdentifier The member being granted a role. This should be a member as specified
   *     by GCP, e.g. groups should be prefixed with the literal 'group:'.
   * @return Binding object granting a custom GCP role to provided user.
   */
  private static Binding buildBinding(
      WsmResourceType resourceType,
      ControlledResourceIamRole resourceRole,
      String projectId,
      String memberIdentifier) {
    CustomGcpIamRole customRole =
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES.get(resourceType, resourceRole);
    return Binding.newBuilder()
        .setRole(customRole.getFullyQualifiedRoleName(projectId))
        .setMembers(Collections.singletonList(memberIdentifier))
        .build();
  }
}
