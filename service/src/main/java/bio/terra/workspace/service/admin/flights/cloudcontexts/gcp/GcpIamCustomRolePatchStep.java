package bio.terra.workspace.service.admin.flights.cloudcontexts.gcp;

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
import com.google.api.services.iam.v1.model.CreateRoleRequest;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to update IAM custom project and resource roles in GCP project. */
public class GcpIamCustomRolePatchStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(GcpIamCustomRolePatchStep.class);

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
      if (getCustomRole(customProjectRole, projectId) == null) {
        createCustomRole(customProjectRole, projectId);
      } else {
        updateCustomRole(customProjectRole, projectId);
      }
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
      // Only assigned field will be updated.
      Role role = new Role().setIncludedPermissions(customRole.getIncludedPermissions());
      iamCow
          .projects()
          .roles()
          .patch(customRole.getFullyQualifiedRoleName(projectId), role)
          .execute();
      logger.debug(
          "Updated role {} with permissions {} in project {}",
          customRole.getRoleName(),
          customRole.getIncludedPermissions(),
          projectId);
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
  private void createCustomRole(CustomGcpIamRole customRole, String projectId)
      throws RetryException {
    try {
      Role gcpRole =
          new Role()
              .setIncludedPermissions(customRole.getIncludedPermissions())
              .setTitle(customRole.getRoleName());
      CreateRoleRequest request =
          new CreateRoleRequest().setRole(gcpRole).setRoleId(customRole.getRoleName());
      iamCow.projects().roles().create("projects/" + projectId, request).execute();
      logger.debug(
          "Created role {} with permissions {} in project {}",
          customRole.getRoleName(),
          customRole.getIncludedPermissions(),
          projectId);
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
        logger.debug(
            "Updated role {} with permissions {} in project {}",
            originalRole.getName(),
            originalRole.getIncludedPermissions(),
            projectId);
      } catch (IOException e) {
        handleIOException(e, customGcpIamRoles.getFullyQualifiedRoleName(projectId));
      }
    } else {
      try {
        iamCow
            .projects()
            .roles()
            .delete(customGcpIamRoles.getFullyQualifiedRoleName(projectId))
            .execute();
        logger.debug(
            "Deleted role {} with permissions {} in project {}",
            customGcpIamRoles.getRoleName(),
            customGcpIamRoles.getIncludedPermissions(),
            projectId);
      } catch (IOException e) {
        handleIOException(e, customGcpIamRoles.getFullyQualifiedRoleName(projectId));
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
}
