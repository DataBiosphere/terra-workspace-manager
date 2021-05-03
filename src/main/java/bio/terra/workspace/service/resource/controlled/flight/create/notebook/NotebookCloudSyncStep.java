package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
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
import bio.terra.workspace.service.resource.controlled.flight.create.GcpPolicyBuilder;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.notebooks.v1.model.Binding;
import com.google.api.services.notebooks.v1.model.Policy;
import com.google.api.services.notebooks.v1.model.SetIamPolicyRequest;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to sync Sam policy groups for the resource to GCP permissions. */
public class NotebookCloudSyncStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(NotebookCloudSyncStep.class);
  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final WorkspaceService workspaceService;

  public NotebookCloudSyncStep(
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    List<Binding> newBindings = createBindings(projectId, flightContext.getWorkingMap());

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    InstanceName instanceName = resource.toInstanceName(projectId);
    try {
      Policy policy = notebooks.instances().getIamPolicy(instanceName).execute();
      List<Binding> existingBindings =
          Optional.ofNullable(policy.getBindings()).orElse(ImmutableList.of());
      if (existingBindings.stream()
          .anyMatch(binding -> newBindings.get(0).getRole().equals(binding.getRole()))) {
        logger.info("AI Notebook instance {} bindings already set.", instanceName.formatName());
        return StepResult.getStepResultSuccess();
      }
      newBindings.addAll(existingBindings);
      policy.setBindings(newBindings);
      notebooks.instances().setIamPolicy(instanceName, new SetIamPolicyRequest().setPolicy(policy));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates the bindings to set on the Notebook instance.
   *
   * <p>{@link GcpPolicyBuilder} works in com.google.cloud.Policy objects, but these are not used by
   * the notebooks API. Transform the com.google.cloud.Policy into a list of bindings to use for the
   * GCP Notebooks API.
   */
  private List<Binding> createBindings(String projectId, FlightMap workingMap) {
    GcpPolicyBuilder policyBuilder =
        new GcpPolicyBuilder(resource, projectId, com.google.cloud.Policy.newBuilder().build());

    // Read Sam groups for each workspace role. Stairway does not
    // have a cleaner way of deserializing parameterized types, so we suppress warnings here.
    @SuppressWarnings("unchecked")
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, Map.class);
    for (Map.Entry<WsmIamRole, String> entry : workspaceRoleGroupsMap.entrySet()) {
      policyBuilder.addWorkspaceBinding(entry.getKey(), entry.getValue());
    }

    // Resources with permissions given to individual users (private or application managed) use
    // the resource's Sam policies to manage those individuals, so they must be synced here.
    // This section should also run for application managed resources, once those are supported.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      @SuppressWarnings("unchecked")
      Map<ControlledResourceIamRole, String> resourceRoleGroupsMap =
          workingMap.get(
              WorkspaceFlightMapKeys.ControlledResourceKeys.IAM_RESOURCE_GROUP_EMAIL_MAP,
              Map.class);
      for (Map.Entry<ControlledResourceIamRole, String> entry : resourceRoleGroupsMap.entrySet()) {
        policyBuilder.addResourceBinding(entry.getKey(), entry.getValue());
      }
    }
    com.google.cloud.Policy gcpPolicy = policyBuilder.build();
    return gcpPolicy.getBindingsList().stream()
        .map(NotebookCloudSyncStep::convertBinding)
        .collect(Collectors.toList());
  }

  private static Binding convertBinding(com.google.cloud.Binding binding) {
    Preconditions.checkArgument(
        binding.getCondition() == null, "Condition conversion not implemented");
    return new Binding().setMembers(binding.getMembers()).setRole(binding.getRole());
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
