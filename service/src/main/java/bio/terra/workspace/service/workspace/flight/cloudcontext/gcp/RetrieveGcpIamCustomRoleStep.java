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
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.ArrayList;

public class RetrieveGcpIamCustomRoleStep implements Step {
  private final IamCow iamCow;
  private final String projectId;

  public RetrieveGcpIamCustomRoleStep(IamCow iamCow, String projectId) {
    this.iamCow = iamCow;
    this.projectId = projectId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ArrayList<Role> customProjectRoles = new ArrayList<>();
    for (CustomGcpIamRole customProjectRole : CUSTOM_GCP_IAM_ROLES) {
      try {
        customProjectRoles.add(
            iamCow
                .projects()
                .roles()
                .get(customProjectRole.getFullyQualifiedRoleName(projectId))
                .execute());
      } catch (IOException e) {
        throw new RetryException(e);
      }
    }
    context.getWorkingMap().put(projectId + CUSTOM_PROJECT_ROLES, customProjectRoles);
    ArrayList<Role> customResourceRoles = new ArrayList<>();
    for (CustomGcpIamRole customResourceRole :
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values()) {
      try {
        customResourceRoles.add(
            iamCow
                .projects()
                .roles()
                .get(customResourceRole.getFullyQualifiedRoleName(projectId))
                .execute());
      } catch (IOException e) {
        throw new RetryException(e);
      }
    }
    context.getWorkingMap().put(projectId + CUSTOM_RESOURCE_ROLES, customResourceRoles);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
