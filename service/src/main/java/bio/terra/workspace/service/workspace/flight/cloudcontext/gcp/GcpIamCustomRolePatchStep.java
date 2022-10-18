package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_IAM_ROLES;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.HashSet;

/** Step to update IAM custom project and resource roles in GCP project. */
public class GcpIamCustomRolePatchStep implements Step {

  private final IamCow iamCow;
  private final String projectId;

  private final HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();

  public GcpIamCustomRolePatchStep(IamCow iamCow, String projectId) {
    this.iamCow = iamCow;
    this.projectId = projectId;
    customGcpIamRoles.addAll(CUSTOM_GCP_IAM_ROLES);
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    for (CustomGcpIamRole customProjectRole : customGcpIamRoles) {
      updateCustomRole(customProjectRole, projectId);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Utility for creating custom roles in GCP from WSM's CustomGcpIamRole objects. These roles will
   * be defined at the project level specified by projectId.
   */
  private void updateCustomRole(CustomGcpIamRole customRole, String projectId)
      throws RetryException {
    String fullyQualifiedRoleName = customRole.getFullyQualifiedRoleName(projectId);
    try {
      Role role = iamCow.projects().roles().get(fullyQualifiedRoleName).execute();
      role.setIncludedPermissions(customRole.getIncludedPermissions());
      iamCow.projects().roles().patch(fullyQualifiedRoleName, role).execute();
    } catch (IOException e) {
      handleIOException(e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {

    for (CustomGcpIamRole customGcpIamRole : customGcpIamRoles) {
      resetCustomRolesToPreviousSetOfPermissions(flightContext, customGcpIamRole);
    }
    return StepResult.getStepResultSuccess();
  }

  private void resetCustomRolesToPreviousSetOfPermissions(
      FlightContext flightContext, CustomGcpIamRole customGcpIamRoles) {
    FlightMap workingMap = flightContext.getWorkingMap();
    Role originalRole =
        workingMap.get(customGcpIamRoles.getFullyQualifiedRoleName(projectId), Role.class);
    if (originalRole != null) {
      try {
        iamCow
            .projects()
            .roles()
            .patch(
                originalRole.getName(),
                new Role().setIncludedPermissions(originalRole.getIncludedPermissions()))
            .execute();
      } catch (IOException e) {
        handleIOException(e);
      }
    } else {
      try {
        iamCow
            .projects()
            .roles()
            .delete(customGcpIamRoles.getFullyQualifiedRoleName(projectId))
            .execute();
      } catch (IOException e) {
        handleIOException(e);
      }
    }
  }

  private void handleIOException(IOException e) {
    if (e instanceof GoogleJsonResponseException googleEx) {
      // If receives client error, do not retry
      if (googleEx.getStatusCode() >= 400 && googleEx.getStatusCode() < 500) {
        return;
      }
    }
    throw new RetryException(e);
  }
}
