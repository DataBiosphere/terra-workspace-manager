package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to set the required GCP policies on the service account used to run the notebook.
 *
 * <p>Because the notebook is being run in service account proxy mode, to create a private notebook
 * instance we have to grant the accessing user exclusive access 'iam.serviceAccount.actAs'
 * permisson on the notebook instance. See {@link CreateAiNotebookInstanceStep}.
 */
public class ServiceAccountPolicyStep implements Step {
  /** Google role for using/acting as a service account. */
  private static final String SERVICE_ACCOUNT_USER_ROLE = "roles/iam.serviceAccountUser";

  private final Logger logger = LoggerFactory.getLogger(ServiceAccountPolicyStep.class);
  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final WorkspaceService workspaceService;
  private final List<ControlledResourceIamRole> privateIamRoles;

  public ServiceAccountPolicyStep(
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      WorkspaceService workspaceService,
      List<ControlledResourceIamRole> privateIamRoles) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
    this.privateIamRoles = privateIamRoles;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    Optional<Binding> maybeBinding = createWriterBinding(flightContext.getWorkingMap());
    if (maybeBinding.isEmpty()) {
      logger.info(
          "No service account binding needed for AI notebook instance resource {}",
          resource.getResourceId());
      return StepResult.getStepResultSuccess();
    }
    List<Binding> newBindings = new ArrayList<>();
    newBindings.add(maybeBinding.get());

    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    IamCow iam = crlService.getIamCow();

    String serviceAccountEmail =
        ServiceAccountName.emailFromAccountId(
            flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class),
            projectId);
    ServiceAccountName serviceAccountName =
        ServiceAccountName.builder().projectId(projectId).email(serviceAccountEmail).build();
    try {
      Policy policy = iam.projects().serviceAccounts().getIamPolicy(serviceAccountName).execute();
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      Optional.ofNullable(policy.getBindings()).ifPresent(newBindings::addAll);
      policy.setBindings(newBindings);
      iam.projects()
          .serviceAccounts()
          .setIamPolicy(serviceAccountName, new SetIamPolicyRequest().setPolicy(policy))
          .execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private Optional<Binding> createWriterBinding(FlightMap workingMap) {
    if (resource.getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_SHARED)) {
      // Read Sam groups for each workspace role. Stairway does not
      // have a cleaner way of deserializing parameterized types, so we suppress warnings here.
      @SuppressWarnings("unchecked")
      Map<WsmIamRole, String> workspaceRoleGroupsMap =
          workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, Map.class);
      String writerGroupEmail = workspaceRoleGroupsMap.get(WsmIamRole.WRITER);
      return Optional.of(
          new Binding()
              .setRole(SERVICE_ACCOUNT_USER_ROLE)
              .setMembers(ImmutableList.of("group:" + writerGroupEmail)));

    } else {
      Preconditions.checkArgument(
          privateIamRoles.stream().anyMatch(role -> role.equals(ControlledResourceIamRole.WRITER)),
          "A private, controlled AI Notebook instance must have the writer role or else it is not useful.");
      if (resource.getAssignedUser().isEmpty()) {
        // There is no user assigned to the private resource, so we cannot create a binding for them
        // yet.
        logger.warn("Unassigned private AI Notebook instance. Assignment is not yet implemented.");
        return Optional.empty();
      }
      String userEmail = resource.getAssignedUser().get();
      return Optional.of(
          new Binding()
              .setRole(SERVICE_ACCOUNT_USER_ROLE)
              .setMembers(ImmutableList.of("user:" + userEmail)));
    }
  }

  /**
   * Because the resource will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
