package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private Logger logger = LoggerFactory.getLogger(GoogleCloudSyncStep.class);

  @Autowired
  public GoogleCloudSyncStep(CloudResourceManagerCow resourceManagerCow) {
    this.resourceManagerCow = resourceManagerCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String gcpProjectName = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    try {
      Policy currentPolicy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectName, new GetIamPolicyRequest())
              .execute();
      List<Binding> existingBindings = currentPolicy.getBindings();

      Map<IamRole, String> samEmailMap =
          Map.of(
              IamRole.READER,
              workingMap.get(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, String.class),
              IamRole.WRITER,
              workingMap.get(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, String.class),
              IamRole.OWNER,
              workingMap.get(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, String.class));
      List<Binding> newBindings = new ArrayList<>();
      for (IamRole iamRole : IamRole.values()) {
        // GCP IAM always prefixes groups with the literal "group:"
        String groupEmail = "group:" + samEmailMap.get(iamRole);
        for (String gcpRole : CloudSyncRoleMapping.cloudSyncRoleMap.get(iamRole)) {
          newBindings.add(
              new Binding().setRole(gcpRole).setMembers(Collections.singletonList(groupEmail)));
        }
      }
      newBindings.addAll(existingBindings);
      Policy newPolicy =
          new Policy()
              .setVersion(currentPolicy.getVersion())
              .setBindings(newBindings)
              .setEtag(currentPolicy.getEtag());
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(newPolicy);
      logger.info("Setting new Cloud Context IAM policy: " + iamPolicyRequest.toPrettyString());
      resourceManagerCow.projects().setIamPolicy(gcpProjectName, iamPolicyRequest).execute();
    } catch (IOException e) {
      throw new RetryException("Error setting IAM permissions", e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Because the project will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
