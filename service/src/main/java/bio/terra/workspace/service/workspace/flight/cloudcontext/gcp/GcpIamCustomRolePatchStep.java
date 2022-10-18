package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_IAM_ROLES;
import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CUSTOM_PROJECT_ROLES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CUSTOM_RESOURCE_ROLES;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpIamCustomRolePatchStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final IamCow iamCow;
  private final String projectId;

  public GcpIamCustomRolePatchStep(IamCow iamCow, String projectId) {
    this.iamCow = iamCow;
    this.projectId = projectId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // First, create the project-level custom roles.
    for (CustomGcpIamRole customProjectRole : CUSTOM_GCP_IAM_ROLES) {
      updateCustomRole(customProjectRole, projectId);
    }
    // Second, create the resource-level custom roles.
    for (CustomGcpIamRole customResourceRole :
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values()) {
      updateCustomRole(customResourceRole, projectId);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Utility for creating custom roles in GCP from WSM's CustomGcpIamRole objects. These roles will
   * be defined at the project level specified by projectId.
   */
  private void updateCustomRole(CustomGcpIamRole customRole, String projectId)
      throws RetryException {
    try {
      // projects/{PROJECT_ID}/roles/{CUSTOM_ROLE_ID}
      String fullyQualifiedRoleName = customRole.getFullyQualifiedRoleName(projectId);
      Role role = iamCow.projects().roles().get(fullyQualifiedRoleName).execute();
      role.setIncludedPermissions(customRole.getIncludedPermissions());
      iamCow.projects().roles().patch(fullyQualifiedRoleName, role).execute();
    } catch (IOException e) {
      // Retry on IO exceptions thrown by CRL.
      throw new RetryException(e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
    customGcpIamRoles.addAll(CUSTOM_GCP_IAM_ROLES);
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
    resetCustomRolesToPreviousSetOfPermissions(flightContext, customGcpIamRoles);

    return StepResult.getStepResultSuccess();
  }

  private void resetCustomRolesToPreviousSetOfPermissions(
      FlightContext flightContext, Set<CustomGcpIamRole> customGcpIamRoles) {
    for(CustomGcpIamRole projectRoles: customGcpIamRoles) {
      Role originalRole = flightContext.getWorkingMap().get(projectRoles.getFullyQualifiedRoleName(projectId), Role.class);
      try {
        iamCow.projects().roles().patch(originalRole.getName(), originalRole);
      } catch (IOException e) {
        throw new RetryException(e);
      }
    }
  }
}
