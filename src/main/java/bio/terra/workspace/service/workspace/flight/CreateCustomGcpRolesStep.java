package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.CustomIamRoleMapping;
import com.google.api.services.iam.v1.model.CreateRoleRequest;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;

public class CreateCustomGcpRolesStep implements Step {

  private final IamCow iamCow;

  public CreateCustomGcpRolesStep(IamCow iamCow) {
    this.iamCow = iamCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    for (var resourceRoleMap : CustomIamRoleMapping.customIamRoleMap.entrySet()) {
      for (var customRoleEntry : resourceRoleMap.getValue().entrySet()) {
        try {
          // Role ids will have the form (resource type)_(role), e.g. GOOGLE_BUCKET_READER.
          String roleName = resourceRoleMap.getKey().name() + "_" + customRoleEntry.getKey().name();
          Role customRole = new Role().setIncludedPermissions(customRoleEntry.getValue());
          CreateRoleRequest request =
              new CreateRoleRequest().setRole(customRole).setRoleId(roleName);
          System.out.println(
              "Creating role with name " + roleName + " and role " + customRole.toPrettyString());
          iamCow.projects().roles().create("projects/" + projectId, request).execute();
        } catch (IOException e) {
          // Retry on IO exceptions thrown by CRL.
          throw new RetryException(e);
        }
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
