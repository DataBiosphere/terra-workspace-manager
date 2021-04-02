package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.iam.ControlledResourceInheritanceMapping;
import bio.terra.workspace.service.iam.CustomGcpIamRole;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Common code for applying Sam IAM policies to GCP for controlled resources. */
public class ControlledResourceCloudSyncUtils {

  /**
   * Updates a provided Policy object to include permissions for appropriate Sam groups.
   *
   * <p>This follows the read-modify-write pattern of modifying permissions on cloud objects to
   * avoid clobbering existing IAM bindings.
   *
   * @param resource The controlled resource bindings are being build for
   * @param projectId The ID of the GCP project permissions are applied to
   * @param currentPolicy The current IAM policy on the cloud object
   * @param workingMap The Stairway working flight map. This method expects Sam group emails to be
   *     provided in this list using the appropriate WorkspaceFlightMapKeys
   * @return
   */
  public static Policy updatePolicyWithSamGroups(
      ControlledResource resource, String projectId, Policy currentPolicy, FlightMap workingMap) {
    String workspaceReaderGroup =
        toGroup(workingMap.get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class));
    String workspaceWriterGroup =
        toGroup(workingMap.get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class));
    String workspaceApplicationGroup =
        toGroup(workingMap.get(WorkspaceFlightMapKeys.IAM_APPLICATION_GROUP_EMAIL, String.class));
    String workspaceOwnerGroup =
        toGroup(workingMap.get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class));

    List<Binding> bindings = new ArrayList<>();
    bindings.addAll(
        bindingsForWorkspaceRole(resource, WsmIamRole.READER, workspaceReaderGroup, projectId));
    bindings.addAll(
        bindingsForWorkspaceRole(resource, WsmIamRole.WRITER, workspaceWriterGroup, projectId));
    bindings.addAll(
        bindingsForWorkspaceRole(
            resource, WsmIamRole.APPLICATION, workspaceApplicationGroup, projectId));
    bindings.addAll(
        bindingsForWorkspaceRole(resource, WsmIamRole.OWNER, workspaceOwnerGroup, projectId));
    bindings.addAll(currentPolicy.getBindingsList());

    // Resources with permissions given to individual users (private or application managed) use
    // the resource's Sam policies to manage those individuals, so they must be synced here.
    // This section should also run for application managed resources, once those are supported.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      String resourceReaderGroup =
          toGroup(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_READER_GROUP_EMAIL, String.class));
      String resourceWriterGroup =
          toGroup(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_WRITER_GROUP_EMAIL, String.class));
      String resourceEditorGroup =
          toGroup(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_EDITOR_GROUP_EMAIL, String.class));

      bindings.add(
          buildBinding(resource, ControlledResourceIamRole.READER, projectId, resourceReaderGroup));
      bindings.add(
          buildBinding(resource, ControlledResourceIamRole.WRITER, projectId, resourceWriterGroup));
      bindings.add(
          buildBinding(resource, ControlledResourceIamRole.EDITOR, projectId, resourceEditorGroup));
    }

    return Policy.newBuilder()
        .setVersion(currentPolicy.getVersion())
        .setBindings(bindings)
        .setEtag(currentPolicy.getEtag())
        .build();
  }

  /**
   * GCP expects all groups to be prepended with the literal "group:" in IAM permissions bindings.
   */
  private static String toGroup(String samEmail) {
    return "group:" + samEmail;
  }

  /**
   * Build a list of role bindings for a given workspace-level role on a controlled resource, using
   * ControlledResourceInheritanceMapping.
   *
   * @param resource The resource these bindings will apply to.
   * @param role The workspace-level role granted to this user. Translated to GCP resource-specific
   *     roles using ControlledResourceInheritanceMapping.
   * @param group The group being granted a role. Should be prefixed with the literal "group:" for
   *     GCP.
   * @param projectId The GCP project ID
   */
  private static List<Binding> bindingsForWorkspaceRole(
      ControlledResource resource, WsmIamRole role, String group, String projectId) {
    return ControlledResourceInheritanceMapping.getInheritanceMapping(
            resource.getAccessScope(), resource.getManagedBy())
        .get(role)
        .stream()
        .map(resourceRole -> buildBinding(resource, resourceRole, projectId, group))
        .collect(Collectors.toList());
  }

  /**
   * Convenience for building a Binding object granting a custom GCP role on a resource to a single
   * member.
   *
   * @param resource The resource this binding will apply to
   * @param role The role being granted on a resource
   * @param projectId ID of the GCP project
   * @param memberEmail The user being granted a role
   * @return Binding object granting a custom GCP role to provided user.
   */
  private static Binding buildBinding(
      ControlledResource resource,
      ControlledResourceIamRole role,
      String projectId,
      String memberEmail) {
    return Binding.newBuilder()
        .setRole(fullyQualifiedRoleName(resource.getResourceType(), role, projectId))
        .setMembers(Collections.singletonList(memberEmail))
        .build();
  }

  /**
   * Return the name of the GCP custom IAM role defined for a given resource role and resource type.
   * This is the same naming function used when building the roles at project creation time, which
   * ensures they exist and that their names match here.
   */
  private static String fullyQualifiedRoleName(
      WsmResourceType resourceType, ControlledResourceIamRole role, String projectId) {
    String roleName = CustomGcpIamRole.customGcpRoleName(resourceType, role);
    return String.format("projects/%s/roles/%s", projectId, roleName);
  }
}
