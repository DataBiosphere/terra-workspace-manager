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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.services.notebooks.v1.model.Binding;
import com.google.api.services.notebooks.v1.model.Policy;
import com.google.api.services.notebooks.v1.model.SetIamPolicyRequest;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to sync Sam policy groups for the resource to GCP permissions. */
public class NotebookCloudSyncStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(NotebookCloudSyncStep.class);
  private final ControlledResourceService controlledResourceService;
  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final GcpCloudContextService gcpCloudContextService;
  private final AuthenticatedUserRequest userRequest;

  public NotebookCloudSyncStep(
      ControlledResourceService controlledResourceService,
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      GcpCloudContextService gcpCloudContextService,
      AuthenticatedUserRequest userRequest) {
    this.controlledResourceService = controlledResourceService;
    this.crlService = crlService;
    this.resource = resource;
    this.gcpCloudContextService = gcpCloudContextService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    List<Binding> newBindings = createBindings(projectId, flightContext.getWorkingMap());

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    InstanceName instanceName = resource.toInstanceName(projectId);
    try {
      Policy policy = notebooks.instances().getIamPolicy(instanceName).execute();
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      Optional.ofNullable(policy.getBindings()).ifPresent(newBindings::addAll);
      policy.setBindings(newBindings);
      notebooks
          .instances()
          .setIamPolicy(instanceName, new SetIamPolicyRequest().setPolicy(policy))
          .execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates the bindings to set on the Notebook instance.
   *
   * <p>{@link
   * bio.terra.workspace.service.resource.controlled.ControlledResourceService#configureGcpPolicyForResource}
   * works in com.google.cloud.Policy objects, but these are not used by the notebooks API.
   * Transform the com.google.cloud.Policy into a list of bindings to use for the GCP Notebooks API.
   */
  private List<Binding> createBindings(String projectId, FlightMap workingMap)
      throws InterruptedException {
    com.google.cloud.Policy currentPolicy = com.google.cloud.Policy.newBuilder().build();
    com.google.cloud.Policy newPolicy =
        controlledResourceService.configureGcpPolicyForResource(
            resource, projectId, currentPolicy, userRequest);

    return newPolicy.getBindingsList().stream()
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
