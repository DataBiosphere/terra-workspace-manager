package bio.terra.workspace.service.admin.flights.cloudcontexts.gcp;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to retrieve IAM custom project and resource roles in GCP projects. */
public class RetrieveGcpIamCustomRoleStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(RetrieveGcpIamCustomRoleStep.class);
  private final IamCow iamCow;
  private final String projectId;

  public RetrieveGcpIamCustomRoleStep(IamCow iamCow, String projectId) {
    this.iamCow = iamCow;
    this.projectId = projectId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Set<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
    customGcpIamRoles.addAll(new CloudSyncRoleMapping().getCustomGcpIamRoles());
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
    return retrieveCustomRoles(customGcpIamRoles, context);
  }

  private StepResult retrieveCustomRoles(
      Collection<CustomGcpIamRole> customGcpIamRoles, FlightContext context) {
    for (CustomGcpIamRole customGcpIamRole : customGcpIamRoles) {
      String fullyQualifiedRoleName = customGcpIamRole.getFullyQualifiedRoleName(projectId);
      Role role;
      try {
        role = iamCow.projects().roles().get(fullyQualifiedRoleName).execute();
      } catch (IOException e) {
        if (e instanceof GoogleJsonResponseException googleEx) {
          // If receives client error, do not retry
          if (googleEx.getStatusCode() >= 400 && googleEx.getStatusCode() < 500) {
            logger.error(
                "calling GCP iam/roles GET api receives error for custom role {}",
                customGcpIamRole,
                e);
            continue;
          }
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
      // Do not put the role in the map if it is already deleted.
      if (role.getDeleted() == null || !role.getDeleted()) {
        context.getWorkingMap().put(fullyQualifiedRoleName, role);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
