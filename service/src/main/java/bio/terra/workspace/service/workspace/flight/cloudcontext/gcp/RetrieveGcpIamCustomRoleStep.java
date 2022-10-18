package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_IAM_ROLES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CUSTOM_PROJECT_ROLES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CUSTOM_RESOURCE_ROLES;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import org.apache.http.HttpStatus;

public class RetrieveGcpIamCustomRoleStep implements Step {
  private final IamCow iamCow;
  private final String projectId;

  public RetrieveGcpIamCustomRoleStep(IamCow iamCow, String projectId) {
    this.iamCow = iamCow;
    this.projectId = projectId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
    customGcpIamRoles.addAll(CUSTOM_GCP_IAM_ROLES);
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
    retrieveCustomRoles(customGcpIamRoles, context);
    return StepResult.getStepResultSuccess();
  }

  private void retrieveCustomRoles(Collection<CustomGcpIamRole> customGcpIamRoles,
      FlightContext context) {
    for (CustomGcpIamRole customResourceRole :
        customGcpIamRoles) {
      try {
        String fullyQualifiedRoleName = customResourceRole.getFullyQualifiedRoleName(projectId);
        Role role = iamCow
            .projects()
            .roles()
            .get(fullyQualifiedRoleName)
            .execute();
        context.getWorkingMap().put(fullyQualifiedRoleName, role);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() != HttpStatus.SC_NOT_FOUND &&
        e.getStatusCode() != HttpStatus.SC_FORBIDDEN) {
          // do not retry if throws 404 or 403.
          throw new RetryException(e);
        }
      } catch (IOException e) {
        throw new RetryException(e);
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
