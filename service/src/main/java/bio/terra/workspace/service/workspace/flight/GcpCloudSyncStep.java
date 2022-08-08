package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.exceptions.RetryableCrlException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Step} that grants GCP IAM permissions to Sam policy groups.
 *
 * <p>This step will grant GCP IAM roles to the google groups underlying Sam policies. It follows a
 * read-modify-write pattern using GCP's field eTag to ensure the write does not clobber other
 * changes. The read-modify-write pattern is necessary to support existing IAM groups which the
 * Buffer Service may grant on projects before handing them out.
 *
 * <p>The "modify" part of this step specifically adds GCP bindings as specified in {@link
 * CloudSyncRoleMapping}. Note that the bindings list sent to GCP may contain multiple entries with
 * the same role. This is valid, though GCP will condense them into one binding per role internally.
 *
 * <p>TODO(PF-624): this step is only used for granting project-level permissions. Once we
 * transition to fully using resource-level permissions, this step can be deleted.
 */
public class GcpCloudSyncStep implements Step {

  private final CloudResourceManagerCow resourceManagerCow;

  private final Logger logger = LoggerFactory.getLogger(GcpCloudSyncStep.class);

  public GcpCloudSyncStep(CloudResourceManagerCow resourceManagerCow) {
    this.resourceManagerCow = resourceManagerCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String gcpProjectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    // Read Sam groups for each workspace role.
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, new TypeReference<>() {});

    try {
      Policy currentPolicy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectId, new GetIamPolicyRequest())
              .execute();

      List<Binding> newBindings = new ArrayList<>();
      // Add all existing bindings to ensure we don't accidentally clobber existing permissions.
      newBindings.addAll(currentPolicy.getBindings());
      // Add appropriate project-level roles for each WSM IAM role.
      workspaceRoleGroupsMap.forEach(
          (role, email) -> {
            Optional<CustomGcpIamRole> customRoleOptional = CUSTOM_GCP_PROJECT_IAM_ROLES.get(role);
            if (customRoleOptional.isPresent()) {
              newBindings.add(bindingForRole(customRoleOptional.get(), email, gcpProjectId));
            }
          });

      Policy newPolicy =
          new Policy()
              .setVersion(currentPolicy.getVersion())
              .setBindings(newBindings)
              .setEtag(currentPolicy.getEtag());
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(newPolicy);
      logger.info("Setting new Cloud Context IAM policy: " + iamPolicyRequest.toPrettyString());
      resourceManagerCow.projects().setIamPolicy(gcpProjectId, iamPolicyRequest).execute();
    } catch (IOException e) {
      throw new RetryableCrlException("Error setting IAM permissions", e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * GCP expects all groups to be prepended with the literal "group:" in IAM permissions bindings.
   */
  private String toMemberIdentifier(String samEmail) {
    return "group:" + samEmail;
  }

  /**
   * Build the project-level role binding for a given group, using CloudSyncRoleMapping.
   *
   * @param customRole GCP custom role
   * @param email The email of the Google group being granted a role.
   * @param gcpProjectId The ID of the project the custom role is defined in.
   */
  private Binding bindingForRole(CustomGcpIamRole customRole, String email, String gcpProjectId) {
    return new Binding()
        .setRole(customRole.getFullyQualifiedRoleName(gcpProjectId))
        .setMembers(Collections.singletonList(toMemberIdentifier(email)));
  }

  /**
   * Because the project will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
