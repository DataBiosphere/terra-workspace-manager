package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_IAM_ROLES;
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
import java.util.List;
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
    resetCustomRolesToPreviousSetOfPermissions(flightContext, CUSTOM_PROJECT_ROLES);
    resetCustomRolesToPreviousSetOfPermissions(flightContext, CUSTOM_RESOURCE_ROLES);

    return StepResult.getStepResultSuccess();
  }

  private void resetCustomRolesToPreviousSetOfPermissions(
      FlightContext flightContext, String workingMapKey) {
    List<Role> originalProjectRole =
        flightContext.getWorkingMap().get(projectId + workingMapKey, new TypeReference<>() {});
    for (Role role : originalProjectRole) {
      try {
        iamCow.projects().roles().patch(role.getName(), role);
      } catch (IOException e) {
        throw new RetryException(e);
      }
    }
  }
}
