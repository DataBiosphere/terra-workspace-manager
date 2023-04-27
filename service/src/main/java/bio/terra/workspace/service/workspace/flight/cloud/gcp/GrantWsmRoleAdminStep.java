package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A step which grants the WSM SA the "Role Admin" permission on the GCP project, which allows it to
 * create custom GCP roles. Unlike other WSM SA permissions, this cannot be granted at the folder
 * level, and so must be applied on each individual project.
 */
public class GrantWsmRoleAdminStep implements Step {

  private final CrlService crlService;

  public GrantWsmRoleAdminStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String wsmSaEmail = GcpUtils.getWsmSaEmail(crlService.getApplicationCredentials());
    String projectId =
        context.getWorkingMap().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    try {
      Policy policy =
          crlService
              .getCloudResourceManagerCow()
              .projects()
              .getIamPolicy(projectId, new GetIamPolicyRequest())
              .execute();
      Binding bindingToAdd =
          new Binding()
              .setMembers(Collections.singletonList("serviceAccount:" + wsmSaEmail))
              .setRole("roles/iam.roleAdmin");
      List<Binding> bindingList = policy.getBindings();
      bindingList.add(bindingToAdd);
      policy.setBindings(bindingList);
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(policy);
      crlService
          .getCloudResourceManagerCow()
          .projects()
          .setIamPolicy(projectId, iamPolicyRequest)
          .execute();
    } catch (IOException e) {
      // Errors here are unexpected and likely transient, WSM should always retry.
      throw new RetryException("Error while granting WSM SA the Role Admin role", e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This project will be deleted elsewhere in the flight, so no need to clean up permissions here
    return StepResult.getStepResultSuccess();
  }
}
