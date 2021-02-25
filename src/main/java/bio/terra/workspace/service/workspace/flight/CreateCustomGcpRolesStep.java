package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.CustomGcpIamRole;
import bio.terra.workspace.service.iam.CustomGcpIamRoleMapping;
import com.google.api.services.iam.v1.model.CreateRoleRequest;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateCustomGcpRolesStep implements Step {

  private final IamCow iamCow;

  private final Logger logger = LoggerFactory.getLogger(CreateCustomGcpRolesStep.class);

  public CreateCustomGcpRolesStep(IamCow iamCow) {
    this.iamCow = iamCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    for (CustomGcpIamRole customRole : CustomGcpIamRoleMapping.customIamRoles) {
      try {
        Role gcpRole = new Role().setIncludedPermissions(customRole.getIncludedPermissions());
        CreateRoleRequest request = new CreateRoleRequest().setRole(gcpRole).setRoleId(customRole.getRoleName());
        logger.info(
            "Creating role {} with permissions {} in project {}",
            customRole.getRoleName(),
            customRole.getIncludedPermissions(),
            projectId);
        iamCow.projects().roles().create("projects/" + projectId, request).execute();
      } catch (IOException e) {
        // Retry on IO exceptions thrown by CRL.
        throw new RetryException(e);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No need to delete roles if the project is being deleted.
    return StepResult.getStepResultSuccess();
  }
}
