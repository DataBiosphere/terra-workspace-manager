package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.CreateRoleRequest;
import com.google.api.services.iam.v1.model.Role;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * This step creates custom role definitions in our GCP context. It does not grant these roles to
 * any users, though other steps do.
 */
public class CreateCustomGcpRolesStep implements Step {

  private final IamCow iamCow;

  private final Logger logger = LoggerFactory.getLogger(CreateCustomGcpRolesStep.class);

  public CreateCustomGcpRolesStep(IamCow iamCow) {
    this.iamCow = iamCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    // First, create the project-level custom roles.
    // Multiple WSM roles may share the same GCP role. De-duping here prevents duplicate requests,
    // which would lead to unnecessary CONFLICT responses from GCP.
    ImmutableSet<CustomGcpIamRole> customProjectRoles =
        CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES.values().stream()
            .collect(ImmutableSet.toImmutableSet());
    for (CustomGcpIamRole customProjectRole : customProjectRoles) {
      createCustomRole(customProjectRole, projectId);
    }
    // Second, create the resource-level custom roles.
    for (CustomGcpIamRole customResourceRole :
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values()) {
      createCustomRole(customResourceRole, projectId);
    }
    return StepResult.getStepResultSuccess();
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
      logger.debug(
          "Creating role {} with permissions {} in project {}",
          customRole.getRoleName(),
          customRole.getIncludedPermissions(),
          projectId);
      iamCow.projects().roles().create("projects/" + projectId, request).execute();
    } catch (GoogleJsonResponseException googleEx) {
      // Because this step may run multiple times, we need to handle duplicate role creation.
      // The project was retrieved from RBS earlier in this flight, so we assume that any conflict
      // of role names must be due to duplicate step execution.
      if (googleEx.getStatusCode() != HttpStatus.CONFLICT.value()) {
        throw new RetryException(googleEx);
      }
    } catch (IOException e) {
      // Retry on IO exceptions thrown by CRL.
      throw new RetryException(e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No need to delete roles if the project is being deleted by other steps in
    // CreateGoogleContextFlight.
    return StepResult.getStepResultSuccess();
  }
}
