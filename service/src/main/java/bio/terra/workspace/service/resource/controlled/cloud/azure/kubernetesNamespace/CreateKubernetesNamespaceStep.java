package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CreateKubernetesNamespaceStep implements Step {
  private final UUID workspaceId;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final ControlledAzureKubernetesNamespaceResource resource;

  public CreateKubernetesNamespaceStep(
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
        kubernetesClientProvider.createCoreApiClient(azureCloudContext, workspaceId);

    try {
      coreApiClient.createNamespace(
          new V1Namespace().metadata(new V1ObjectMeta().name(resource.getKubernetesNamespace())),
          null,
          null,
          null,
          null);
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.CONFLICT);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var coreApiClient =
        kubernetesClientProvider.createCoreApiClient(azureCloudContext, workspaceId);

    try {
      coreApiClient.deleteNamespace(
          resource.getKubernetesNamespace(), null, null, null, null, null, null);
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.NOT_FOUND);
    }
    return StepResult.getStepResultSuccess();
  }
}
