package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.workspace.CloudSyncRoleMapping;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.api.services.cloudresourcemanager.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A {@link Step} that grants GCP IAM permissions to Sam policy groups.
 *
 * <p>This step will grant GCP IAM roles to the google groups underlying Sam policies. It follows a
 * read-modify-write pattern using GCP's field eTag to ensure the write does not clobber other
 * changes. The read-modify-write pattern is necessary to support existing IAM groups which the
 * Buffer Service may grant on projects before handing them out.
 *
 * <p>The "modify" part of this step specifically adds GCP bindings as specified in {@link
 * CloudSyncRoleMapping}. It adds new policy bindings for the specified roles, or modifies them to
 * include Sam groups if they already exist.
 */
public class GoogleCloudSyncStep implements Step {

  CloudResourceManagerCow resourceManagerCow;

  @Autowired
  public GoogleCloudSyncStep(CloudResourceManagerCow resourceManagerCow) {
    this.resourceManagerCow = resourceManagerCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String gcpProjectName =
        "projects/" + flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      Policy currentPolicy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectName, new GetIamPolicyRequest())
              .execute();
      List<Binding> bindings = currentPolicy.getBindings();

      addGroupToRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.OWNER),
          bindings);

      addGroupToRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.WRITER),
          bindings);

      addGroupToRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.READER),
          bindings);

      Policy newPolicy = new Policy().setBindings(bindings).setEtag(currentPolicy.getEtag());
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(newPolicy);
      resourceManagerCow.projects().setIamPolicy(gcpProjectName, iamPolicyRequest).execute();
    } catch (IOException e) {
      throw new RetryException("Error setting IAM permissions", e);
    }
    return StepResult.getStepResultSuccess();
  }

  /** Adds a Sam group to a list of GCP bindings, creating new bindings if they don't exist. */
  private void addGroupToRoleBindings(
      String samGroupEmail, List<String> gcpRoles, List<Binding> existingBindings) {
    List<String> gcpBindingRoleList =
        existingBindings.stream().map(Binding::getRole).collect(Collectors.toList());
    for (String gcpRole : gcpRoles) {
      // Check if a binding for this GCP role already exists. If it does, add the Sam group.
      // If it doesn't, create a new binding for the Sam group.
      int bindingIndex = gcpBindingRoleList.indexOf(gcpRole);
      if (bindingIndex != -1) {
        Binding gcpRoleBinding = existingBindings.get(bindingIndex);
        if (!gcpRoleBinding.getMembers().contains(samGroupEmail)) {
          List<String> modifiedMemberList = gcpRoleBinding.getMembers();
          modifiedMemberList.add(samGroupEmail);
          gcpRoleBinding.setMembers(modifiedMemberList);
        }
      } else {
        Binding newBinding =
            new Binding().setRole(gcpRole).setMembers(Collections.singletonList(samGroupEmail));
        existingBindings.add(newBinding);
      }
    }
  }

  /**
   * Read the GCP project's IAM bindings and remove Sam groups, if present.
   *
   * <p>Because the Sam google groups were synchronized for the first time earlier in this flight,
   * we know that this flight is the only source that could have granted them GCP roles. Therefore,
   * it's always safe to remove those roles in an undo step.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String gcpProjectName =
        "projects/" + flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      Policy currentPolicy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectName, new GetIamPolicyRequest())
              .execute();
      List<Binding> bindings = currentPolicy.getBindings();
      removeGroupFromRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.OWNER),
          bindings);

      removeGroupFromRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.WRITER),
          bindings);

      removeGroupFromRoleBindings(
          flightContext
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class),
          CloudSyncRoleMapping.cloudSyncRoleMap.get(IamRole.READER),
          bindings);

      Policy newPolicy = new Policy().setBindings(bindings).setEtag(currentPolicy.getEtag());
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(newPolicy);
      resourceManagerCow.projects().setIamPolicy(gcpProjectName, iamPolicyRequest).execute();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return StepResult.getStepResultSuccess();
  }

  /** Removes a Sam group from a list of GCP bindings if present. */
  private void removeGroupFromRoleBindings(
      String samGroupEmail, List<String> gcpRoles, List<Binding> existingBindings) {
    List<String> gcpBindingRoleList =
        existingBindings.stream().map(Binding::getRole).collect(Collectors.toList());
    for (String gcpRole : gcpRoles) {
      int bindingIndex = gcpBindingRoleList.indexOf(gcpRole);
      if (bindingIndex != -1) {
        existingBindings.get(bindingIndex).getMembers().remove(samGroupEmail);
      }
    }
  }
}
