package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.exceptions.RetryableCrlException;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    String gcpProjectName = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    // Read Sam groups for each workspace role. Stairway does not
    // have a cleaner way of deserializing parameterized types, so we suppress warnings here.
    @SuppressWarnings("unchecked")
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, Map.class);
    try {
      Policy currentPolicy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectName, new GetIamPolicyRequest())
              .execute();

      List<Binding> newBindings = new ArrayList<>();
      // Add all existing bindings to ensure we don't accidentally clobber existing permissions.
      newBindings.addAll(currentPolicy.getBindings());
      for (Map.Entry<WsmIamRole, String> entry : workspaceRoleGroupsMap.entrySet()) {
        newBindings.addAll(bindingsForRole(entry.getKey(), entry.getValue()));
      }

      Policy newPolicy =
          new Policy()
              .setVersion(currentPolicy.getVersion())
              .setBindings(newBindings)
              .setEtag(currentPolicy.getEtag());
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(newPolicy);
      logger.info("Setting new Cloud Context IAM policy: " + iamPolicyRequest.toPrettyString());
      resourceManagerCow.projects().setIamPolicy(gcpProjectName, iamPolicyRequest).execute();
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
   * Build a list of role bindings for a given group, using CloudSyncRoleMapping.
   *
   * @param role The role granted to this user. Translated to GCP roles using CloudSyncRoleMapping.
   * @param email The email of the Google group being granted a role.
   */
  private List<Binding> bindingsForRole(WsmIamRole role, String email) {
    return CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(role).stream()
        .map(
            gcpRole ->
                new Binding()
                    .setRole(gcpRole)
                    .setMembers(Collections.singletonList(toMemberIdentifier(email))))
        .collect(Collectors.toList());
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
