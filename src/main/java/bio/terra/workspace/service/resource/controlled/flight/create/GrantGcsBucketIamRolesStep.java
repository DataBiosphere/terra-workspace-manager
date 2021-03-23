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
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
  private final Logger logger = LoggerFactory.getLogger(GrantGcsBucketIamRolesStep.class);

  public GrantGcsBucketIamRolesStep(CrlService crlService, ControlledGcsBucketResource resource) {
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();

    // Users do not have read or write access to IAM policies, so this request is executed via
    // WSM's service account.
    Policy currentPolicy =
        crlService
            .createStorageCow(Optional.empty(), Optional.empty())
            .getIamPolicy(resource.getBucketName());

    String readerGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class));
    String writerGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class));
    String applicationGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_APPLICATION_GROUP_EMAIL, String.class));
    String ownerGroup =
        gcpGroupNameFromSamEmail(
            workingMap.get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class));

    List<Binding> newBindings = new ArrayList<>();
    newBindings.addAll(bindingsForRole(WsmIamRole.READER, readerGroup));
    newBindings.addAll(bindingsForRole(WsmIamRole.WRITER, writerGroup));
    newBindings.addAll(bindingsForRole(WsmIamRole.APPLICATION, applicationGroup));
    newBindings.addAll(bindingsForRole(WsmIamRole.OWNER, ownerGroup));
    newBindings.addAll(currentPolicy.getBindingsList());

    Policy newPolicy =
        Policy.newBuilder()
            .setVersion(currentPolicy.getVersion())
            .setBindings(newBindings)
            .setEtag(currentPolicy.getEtag())
            .build();
    logger.info(
        "Granting GCP permissions on bucket {}: {}: ",
        resource.getBucketName(),
        newPolicy.toString());
    // Users do not have read or write access to IAM policies, so this request is executed via
    // WSM's service account.
    crlService
        .createStorageCow(Optional.empty(), Optional.empty())
        .setIamPolicy(resource.getBucketName(), newPolicy);
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
   * @param role The role granted to this user. Translated to GCP roles using
   *     ControlledResourceInheritanceMapping.
   * @param group The group being granted a role. Should be prefixed with the literal "group:" for
   *     GCP.
   */
  private List<Binding> bindingsForRole(WsmIamRole role, String group) {
    return ControlledResourceInheritanceMapping.getInheritanceMapping(
            resource.getAccessScope(), resource.getManagedBy())
        .get(role)
        .stream()
        .map(
            resourceRole ->
                Binding.newBuilder()
                    .setRole(gcpRoleFromResourceRole(resourceRole))
                    .setMembers(Collections.singletonList(group))
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Return the name of the existing GCP custom IAM role defined for a given Resource role and
   * resource type (in this case, GCS Bucket).
   */
  private String gcpRoleFromResourceRole(ControlledResourceIamRole role) {
    return CustomGcpIamRole.customGcpRoleName(WsmResourceType.GCS_BUCKET, role);
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
