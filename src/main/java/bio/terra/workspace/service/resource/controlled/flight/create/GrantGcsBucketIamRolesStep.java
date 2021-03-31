package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.ControlledResourceInheritanceMapping;
import bio.terra.workspace.service.iam.CustomGcpIamRole;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for granting cloud permissions on resources to workspace members. This follows the read-
 * modify-write pattern of modifying permissions on cloud objects to avoid clobbering existing IAM
 * bindings.
 */
public class GrantGcsBucketIamRolesStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final WorkspaceService workspaceService;
  private final Logger logger = LoggerFactory.getLogger(GrantGcsBucketIamRolesStep.class);

  public GrantGcsBucketIamRolesStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();

    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());

    // Users do not have read or write access to IAM policies, so this request is executed via
    // WSM's service account.
    Policy currentPolicy =
        crlService.createStorageCow(projectId).getIamPolicy(resource.getBucketName());

    String workspaceReaderGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class));
    String workspaceWriterGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class));
    String workspaceApplicationGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_APPLICATION_GROUP_EMAIL, String.class));
    String workspaceOwnerGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class));

    List<Binding> bindings = new ArrayList<>();
    bindings.addAll(bindingsForWorkspaceRole(WsmIamRole.READER, workspaceReaderGroup, projectId));
    bindings.addAll(bindingsForWorkspaceRole(WsmIamRole.WRITER, workspaceWriterGroup, projectId));
    bindings.addAll(
        bindingsForWorkspaceRole(WsmIamRole.APPLICATION, workspaceApplicationGroup, projectId));
    bindings.addAll(bindingsForWorkspaceRole(WsmIamRole.OWNER, workspaceOwnerGroup, projectId));
    bindings.addAll(currentPolicy.getBindingsList());

    // Resources with permissions given to individual users (private or application managed) use
    // the resource's Sam policies to manage those individuals, so they must be synced here.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
        || resource.getManagedBy() == ManagedByType.MANAGED_BY_APPLICATION) {
      String resourceReaderGroup =
          gcpGroupNameFromSamEmail(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_READER_GROUP_EMAIL, String.class));
      String resourceWriterGroup =
          gcpGroupNameFromSamEmail(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_WRITER_GROUP_EMAIL, String.class));
      String resourceEditorGroup =
          gcpGroupNameFromSamEmail(
              workingMap.get(ControlledResourceKeys.IAM_RESOURCE_EDITOR_GROUP_EMAIL, String.class));

      bindings.add(buildBinding(ControlledResourceIamRole.READER, projectId, resourceReaderGroup));
      bindings.add(buildBinding(ControlledResourceIamRole.WRITER, projectId, resourceWriterGroup));
      bindings.add(buildBinding(ControlledResourceIamRole.EDITOR, projectId, resourceEditorGroup));
    }

    Policy newPolicy =
        Policy.newBuilder()
            .setVersion(currentPolicy.getVersion())
            .setBindings(bindings)
            .setEtag(currentPolicy.getEtag())
            .build();
    logger.info(
        "Syncing workspace roles to GCP permissions on bucket {}", resource.getBucketName());

    crlService.createStorageCow(projectId).setIamPolicy(resource.getBucketName(), newPolicy);
    return StepResult.getStepResultSuccess();
  }

  /**
   * GCP expects all groups to be prepended with the literal "group:" in IAM permissions bindings.
   */
  private String gcpGroupNameFromSamEmail(String samEmail) {
    return "group:" + samEmail;
  }

  /**
   * Build a list of role bindings for a given group, using ControlledResourceInheritanceMapping.
   *
   * @param role The workspace-level role granted to this user. Translated to GCP resource-specific
   *     roles using ControlledResourceInheritanceMapping.
   * @param group The group being granted a role. Should be prefixed with the literal "group:" for
   *     GCP.
   * @param projectId The GCP project ID
   */
  private List<Binding> bindingsForWorkspaceRole(WsmIamRole role, String group, String projectId) {
    return ControlledResourceInheritanceMapping.getInheritanceMapping(
            resource.getAccessScope(), resource.getManagedBy())
        .get(role)
        .stream()
        .map(resourceRole -> buildBinding(resourceRole, projectId, group))
        .collect(Collectors.toList());
  }

  /**
   * Convenience for building a Binding object granting a custom GCP role to a single member.
   *
   * @param role The role being granted on a resource
   * @param projectId ID of the GCP project
   * @param memberEmail The user being granted a role
   * @return Binding object granting a custom GCP role to provided user.
   */
  private Binding buildBinding(
      ControlledResourceIamRole role, String projectId, String memberEmail) {
    return Binding.newBuilder()
        .setRole(fullyQualifiedRoleName(role, projectId))
        .setMembers(Collections.singletonList(memberEmail))
        .build();
  }

  /**
   * Return the name of the existing GCP custom IAM role defined for a given resource role (e.g.
   * reader, writer) for GCS buckets.
   */
  private String fullyQualifiedRoleName(ControlledResourceIamRole role, String projectId) {
    String roleName = CustomGcpIamRole.customGcpRoleName(WsmResourceType.GCS_BUCKET, role);
    return String.format("projects/%s/roles/%s", projectId, roleName);
  }

  /**
   * Because the resource will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
