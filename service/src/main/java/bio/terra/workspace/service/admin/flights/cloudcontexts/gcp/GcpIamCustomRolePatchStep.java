package bio.terra.workspace.service.admin.flights.cloudcontexts.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.UPDATED_WORKSPACES;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.workspace.GcpCloudSyncRoleMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.CreateRoleRequest;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to update IAM custom project and resource roles in GCP project. */
public class GcpIamCustomRolePatchStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(GcpIamCustomRolePatchStep.class);

  private final IamCow iamCow;
  private final UUID workspaceId;
  private final String projectId;
  private final boolean isWetRun;

  private final HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
  private boolean workspaceUpdated = false;

  public GcpIamCustomRolePatchStep(
      GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping,
      IamCow iamCow,
      UUID workspaceId,
      String projectId,
      boolean isWetRun) {
    this.iamCow = iamCow;
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.isWetRun = isWetRun;
    customGcpIamRoles.addAll(gcpCloudSyncRoleMapping.getCustomGcpIamRoles());
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    for (CustomGcpIamRole customGcpIamRole : customGcpIamRoles) {
      Role originalRole = getCustomRole(customGcpIamRole, projectId);
      if (originalRole == null) {
        createCustomRole(customGcpIamRole);
      } else {
        if (!CollectionUtils.isEqualCollection(
            originalRole.getIncludedPermissions(), customGcpIamRole.getIncludedPermissions())) {
          updateCustomRole(customGcpIamRole, originalRole.getIncludedPermissions());
        }
      }
    }
    maybeRecordWorkspaceIsUpdated(flightContext);
    return StepResult.getStepResultSuccess();
  }

  /**
   * Utility for creating custom roles in GCP from WSM's CustomGcpIamRole objects. These roles will
   * be defined at the project level specified by projectId.
   */
  private void updateCustomRole(CustomGcpIamRole customRole, List<String> originalPermissions)
      throws RetryException {
    try {
      if (isWetRun) {
        // Only assigned field will be updated.
        Role role = new Role().setIncludedPermissions(customRole.getIncludedPermissions());
        iamCow
            .projects()
            .roles()
            .patch(customRole.getFullyQualifiedRoleName(projectId), role)
            .execute();
      }
      logger.info(
          "Updated role {} with permissions {} removed or added in project {}",
          customRole.getRoleName(),
          CollectionUtils.disjunction(customRole.getIncludedPermissions(), originalPermissions),
          projectId);
      workspaceUpdated = true;
    } catch (IOException e) {
      handleIOException(e, customRole.getFullyQualifiedRoleName(projectId));
    }
  }

  private Role getCustomRole(CustomGcpIamRole customRole, String projectId) {
    String fullyQualifiedRoleName = customRole.getFullyQualifiedRoleName(projectId);
    try {
      return iamCow.projects().roles().get(fullyQualifiedRoleName).execute();
    } catch (IOException e) {
      handleIOException(e, customRole.getFullyQualifiedRoleName(projectId));
      return null;
    }
  }

  /**
   * Utility for creating custom roles in GCP from WSM's CustomGcpIamRole objects. These roles will
   * be defined at the project level in the specified by projectId.
   */
  private void createCustomRole(CustomGcpIamRole customRole) throws RetryException {
    try {
      if (isWetRun) {
        Role gcpRole =
            new Role()
                .setIncludedPermissions(customRole.getIncludedPermissions())
                .setTitle(customRole.getRoleName());
        CreateRoleRequest request =
            new CreateRoleRequest().setRole(gcpRole).setRoleId(customRole.getRoleName());
        iamCow.projects().roles().create("projects/" + projectId, request).execute();
      }
      logger.info(
          "Created role {} with permissions {} in project {}",
          customRole.getRoleName(),
          customRole.getIncludedPermissions(),
          projectId);
      workspaceUpdated = true;
    } catch (IOException e) {
      // Retry on IO exceptions thrown by CRL.
      handleIOException(e, customRole.getFullyQualifiedRoleName(projectId));
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    for (CustomGcpIamRole customGcpIamRole : customGcpIamRoles) {
      resetCustomRolesToPreviousSetOfPermissions(flightContext, customGcpIamRole);
    }
    maybeRecordWorkspaceIsUpdated(flightContext);
    return StepResult.getStepResultSuccess();
  }

  private void resetCustomRolesToPreviousSetOfPermissions(
      FlightContext flightContext, CustomGcpIamRole customGcpIamRoles) {
    FlightMap workingMap = flightContext.getWorkingMap();
    Role originalRole =
        workingMap.get(customGcpIamRoles.getFullyQualifiedRoleName(projectId), Role.class);
    if (originalRole != null) {
      try {
        if (isWetRun) {
          iamCow
              .projects()
              .roles()
              .patch(
                  originalRole.getName(),
                  new Role().setIncludedPermissions(originalRole.getIncludedPermissions()))
              .execute();
        }
        logger.info(
            "UndoStep: revert role {} with permissions {} in project {}",
            originalRole.getName(),
            originalRole.getIncludedPermissions(),
            projectId);
        workspaceUpdated = false;
      } catch (IOException e) {
        handleIOException(e, customGcpIamRoles.getFullyQualifiedRoleName(projectId));
        workspaceUpdated = true;
      }
    } else {
      try {
        if (isWetRun) {
          iamCow
              .projects()
              .roles()
              .delete(customGcpIamRoles.getFullyQualifiedRoleName(projectId))
              .execute();
          logger.info(
              "Deleted role {} with permissions {} in project {}",
              customGcpIamRoles.getRoleName(),
              customGcpIamRoles.getIncludedPermissions(),
              projectId);
        }
        workspaceUpdated = false;
      } catch (IOException e) {
        handleIOException(e, customGcpIamRoles.getFullyQualifiedRoleName(projectId));
        workspaceUpdated = true;
      }
    }
  }

  private void handleIOException(IOException e, String customRole) {
    if (e instanceof GoogleJsonResponseException googleEx) {
      // If receives client error, do not retry
      if (googleEx.getStatusCode() >= 400 && googleEx.getStatusCode() < 500) {
        logger.error("calling GCP iam/roles api receives error for custom role {}", customRole, e);
        return;
      }
    }
    throw new RetryException(e);
  }

  private void maybeRecordWorkspaceIsUpdated(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    HashSet<String> updatedWorkspaces =
        workingMap.get(UPDATED_WORKSPACES, new TypeReference<>() {});
    if (workspaceUpdated) {
      updatedWorkspaces.add(workspaceId.toString());
    } else {
      // When a step is undo, the change is undone so we need to potentially
      // remove the workspace id from the updated workspaces list.
      updatedWorkspaces.remove(workspaceId.toString());
    }
    workingMap.put(UPDATED_WORKSPACES, updatedWorkspaces);
  }
}
