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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to set the required GCP policies on the service account used for WRITERS to access the
 * notebook instance via the AI Platform proxy.
 *
 * <p>Because the notebook is being run in service account proxy mode, to create a private notebook
 * instance we have to grant the accessing user exclusive access 'iam.serviceAccount.actAs'
 * permisson on the notebook instance. See {@link CreateAiNotebookInstanceStep}.
 */
public class ServiceAccountPolicyStep implements Step {
  /** Google role for using/acting as a service account. */
  private static final String SERVICE_ACCOUNT_USER_ROLE = "roles/iam.serviceAccountUser";

  private final Logger logger = LoggerFactory.getLogger(ServiceAccountPolicyStep.class);
  private final AuthenticatedUserRequest userRequest;
  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final List<ControlledResourceIamRole> privateIamRoles;

  public ServiceAccountPolicyStep(
      AuthenticatedUserRequest userRequest,
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      SamService samService,
      WorkspaceService workspaceService,
      List<ControlledResourceIamRole> privateIamRoles) {
    this.userRequest = userRequest;
    this.crlService = crlService;
    this.resource = resource;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.privateIamRoles = privateIamRoles;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    List<Binding> newBindings = new ArrayList<>();
    newBindings.add(createWriterBinding(flightContext.getWorkingMap()));

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

  private Binding createWriterBinding(FlightMap workingMap) throws InterruptedException {
    Preconditions.checkArgument(
        resource.getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE),
        "Shared controlled AI notebook resources not yet implemented");
    Preconditions.checkArgument(
        privateIamRoles.stream().anyMatch(role -> role.equals(ControlledResourceIamRole.WRITER)),
        "A private, controlled AI Notebook instance must have the writer role or else it is not useful.");

    // Grant permission via private resource's Sam group so that access can be revoked via Sam.
    Map<ControlledResourceIamRole, String> resourceRoleGroupsMap =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.IAM_RESOURCE_GROUP_EMAIL_MAP,
            new TypeReference<>() {});
    String writerGroupEmail = resourceRoleGroupsMap.get(ControlledResourceIamRole.WRITER);
    return new Binding()
        .setRole(SERVICE_ACCOUNT_USER_ROLE)
        .setMembers(Collections.singletonList("group:" + writerGroupEmail));
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
