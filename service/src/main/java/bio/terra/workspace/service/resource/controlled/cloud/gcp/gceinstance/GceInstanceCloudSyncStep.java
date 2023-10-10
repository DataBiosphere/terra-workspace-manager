package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_LOCATION;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.services.compute.model.Binding;
import com.google.api.services.compute.model.Policy;
import com.google.api.services.compute.model.ZoneSetPolicyRequest;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Step to sync Sam policy groups for the resource to GCP permissions. */
public class GceInstanceCloudSyncStep implements Step {
  private final ControlledResourceService controlledResourceService;
  private final CrlService crlService;
  private final ControlledGceInstanceResource resource;
  private final AuthenticatedUserRequest userRequest;

  public GceInstanceCloudSyncStep(
      ControlledResourceService controlledResourceService,
      CrlService crlService,
      ControlledGceInstanceResource resource,
      AuthenticatedUserRequest userRequest) {
    this.controlledResourceService = controlledResourceService;
    this.crlService = crlService;
    this.resource = resource;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    GcpCloudContext cloudContext =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(),
            ControlledResourceKeys.GCP_CLOUD_CONTEXT,
            GcpCloudContext.class);
    String projectId = cloudContext.getGcpProjectId();
    List<Binding> newBindings = createBindings(cloudContext);

    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    String zone =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_LOCATION, String.class);
    try {
      Policy policy =
          cloudComputeCow
              .instances()
              .getIamPolicy(projectId, zone, resource.getInstanceId())
              .execute();
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      Optional.ofNullable(policy.getBindings()).ifPresent(newBindings::addAll);
      policy.setBindings(newBindings);
      cloudComputeCow
          .instances()
          .setIamPolicy(
              projectId,
              zone,
              resource.getInstanceId(),
              new ZoneSetPolicyRequest().setPolicy(policy))
          .execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates the bindings to set on the compute instance.
   *
   * <p>{@link
   * bio.terra.workspace.service.resource.controlled.ControlledResourceService#configureGcpPolicyForResource}
   * works in com.google.cloud.Policy objects, but these are not used by the compute engine API.
   * Transform the com.google.cloud.Policy into a list of bindings to use for the GCP GCE API.
   */
  private List<Binding> createBindings(GcpCloudContext cloudContext) throws InterruptedException {
    com.google.cloud.Policy currentPolicy = com.google.cloud.Policy.newBuilder().build();
    com.google.cloud.Policy newPolicy =
        controlledResourceService.configureGcpPolicyForResource(
            resource, cloudContext, currentPolicy, userRequest);

    return newPolicy.getBindingsList().stream()
        .map(GceInstanceCloudSyncStep::convertBinding)
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
