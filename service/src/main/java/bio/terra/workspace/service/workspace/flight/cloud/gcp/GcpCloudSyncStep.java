package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.grant.GrantService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.workspace.GcpCloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.exceptions.RetryableCrlException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
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
import java.util.UUID;
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
 * GcpCloudSyncRoleMapping}. Note that the bindings list sent to GCP may contain multiple entries
 * with the same role. This is valid, though GCP will condense them into one binding per role
 * internally.
 *
 * <p>TODO(PF-624): this step is only used for granting project-level permissions. Once we
 * transition to fully using resource-level permissions, this step can be deleted.
 */
public class GcpCloudSyncStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(GcpCloudSyncStep.class);
  private final CloudResourceManagerCow resourceManagerCow;
  private final GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping;
  private final FeatureConfiguration features;
  private final SamService samService;
  private final GrantService grantService;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceUuid;

  public GcpCloudSyncStep(
      CloudResourceManagerCow resourceManagerCow,
      GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping,
      FeatureConfiguration features,
      SamService samService,
      GrantService grantService,
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid) {
    this.gcpCloudSyncRoleMapping = gcpCloudSyncRoleMapping;
    this.resourceManagerCow = resourceManagerCow;
    this.features = features;
    this.samService = samService;
    this.grantService = grantService;
    this.userRequest = userRequest;
    this.workspaceUuid = workspaceUuid;
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

      List<Binding> newBindings = new ArrayList<>(currentPolicy.getBindings());

      // Add appropriate project-level roles for each WSM IAM role.
      workspaceRoleGroupsMap.forEach(
          (wsmRole, email) -> {
            if (gcpCloudSyncRoleMapping.getCustomGcpProjectIamRoles().containsKey(wsmRole)) {
              newBindings.add(bindingForRole(wsmRole, GcpUtils.toGroupMember(email), gcpProjectId));
            }
          });

      // Workaround for permission propagation delays: directly grant user and pet the owner
      // role on the project.
      if (features.isTemporaryGrantEnabled()) {
        // Get the user emails we are granting
        AuthenticatedUserRequest petSaCredentials =
            flightContext
                .getWorkingMap()
                .get(WorkspaceFlightMapKeys.PET_SA_CREDENTIALS, AuthenticatedUserRequest.class);
        String petMember = GcpUtils.toSaMember(petSaCredentials.getEmail());
        newBindings.add(bindingForRole(WsmIamRole.OWNER, petMember, gcpProjectId));

        String userEmail = samService.getUserEmailFromSam(userRequest);
        String userMember = null;
        if (grantService.isUserGrantAllowed(userEmail)) {
          userMember = GcpUtils.toUserMember(userEmail);
          newBindings.add(bindingForRole(WsmIamRole.OWNER, userMember, gcpProjectId));
        }

        // Store the temporary grant - it will be revoked in the background
        grantService.recordProjectGrant(
            workspaceUuid,
            userMember,
            petMember,
            getCustomRoleName(WsmIamRole.OWNER, gcpProjectId));
      }

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
   * Build the project-level role binding for a given group, using CloudSyncRoleMapping.
   *
   * @param role The role granted to this user. Translated to GCP roles using CloudSyncRoleMapping.
   * @param member The member being granted; group:email, member:email, serviceAccount:email
   * @param gcpProjectId The ID of the project the custom role is defined in.
   */
  private Binding bindingForRole(WsmIamRole role, String member, String gcpProjectId) {
    return new Binding()
        .setRole(getCustomRoleName(role, gcpProjectId))
        .setMembers(Collections.singletonList(member));
  }

  private String getCustomRoleName(WsmIamRole role, String gcpProjectId) {
    CustomGcpIamRole customRole = gcpCloudSyncRoleMapping.getCustomGcpProjectIamRoles().get(role);
    if (customRole == null) {
      throw new InternalLogicException(String.format("Missing custom GCP project role %s", role));
    }
    return customRole.getFullyQualifiedRoleName(gcpProjectId);
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
