package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class KubernetesNamespaceGuardStep implements Step {
  private final UUID workspaceId;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final ControlledAzureKubernetesNamespaceResource resource;

  public KubernetesNamespaceGuardStep(
      UUID workspaceId,
      KubernetesClientProvider kubernetesClientProvider,
      ControlledAzureKubernetesNamespaceResource resource) {
    this.workspaceId = workspaceId;
    this.kubernetesClientProvider = kubernetesClientProvider;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var coreApiClient =
        kubernetesClientProvider
            .createCoreApiClient(azureCloudContext, workspaceId)
            .orElseThrow(() -> new RuntimeException("No shared cluster found"));

    try {
      var existing = coreApiClient.readNamespace(resource.getKubernetesNamespace(), null);
      if (existing != null) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ConflictException(
                "Namespace already exists: " + resource.getKubernetesNamespace()));
      }
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.NOT_FOUND);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
