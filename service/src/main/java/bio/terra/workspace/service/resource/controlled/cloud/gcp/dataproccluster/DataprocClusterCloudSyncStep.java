package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_DATAPROC_CLUSTER_PARAMETERS;

import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.services.dataproc.model.Binding;
import com.google.api.services.dataproc.model.Policy;
import com.google.api.services.dataproc.model.SetIamPolicyRequest;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to sync Sam policy groups for the resource to GCP permissions. */
public class DataprocClusterCloudSyncStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DataprocClusterCloudSyncStep.class);
  private final ControlledResourceService controlledResourceService;
  private final CrlService crlService;
  private final ControlledDataprocClusterResource resource;
  private final AuthenticatedUserRequest userRequest;

  public DataprocClusterCloudSyncStep(
      ControlledResourceService controlledResourceService,
      CrlService crlService,
      ControlledDataprocClusterResource resource,
      AuthenticatedUserRequest userRequest) {
    this.controlledResourceService = controlledResourceService;
    this.crlService = crlService;
    this.resource = resource;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    GcpCloudContext cloudContext =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);

    ApiGcpDataprocClusterCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_DATAPROC_CLUSTER_PARAMETERS, ApiGcpDataprocClusterCreationParameters.class);

    DataprocCow dataprocCow = crlService.getDataprocCow();

    List<Binding> newBindings = createBindings(cloudContext);
    String region = creationParameters.getRegion();
    ClusterName clusterName = resource.toClusterName(region);
    try {
      Policy policy = dataprocCow.clusters().getIamPolicy(clusterName).execute();
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      Optional.ofNullable(policy.getBindings()).ifPresent(newBindings::addAll);
      policy.setBindings(newBindings);
      dataprocCow
          .clusters()
          .setIamPolicy(clusterName, new SetIamPolicyRequest().setPolicy(policy))
          .execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates the bindings to set on the Dataproc cluster.
   *
   * <p>{@link ControlledResourceService#configureGcpPolicyForResource} works in
   * com.google.cloud.Policy objects, but these are not used by the clusters API. Transform the
   * com.google.cloud.Policy into a list of bindings to use for the Dataproc clusters. API.
   */
  private List<Binding> createBindings(GcpCloudContext cloudContext) throws InterruptedException {
    com.google.cloud.Policy currentPolicy = com.google.cloud.Policy.newBuilder().build();
    com.google.cloud.Policy newPolicy =
        controlledResourceService.configureGcpPolicyForResource(
            resource, cloudContext, currentPolicy, userRequest);

    return newPolicy.getBindingsList().stream()
        .map(DataprocClusterCloudSyncStep::convertBinding)
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
