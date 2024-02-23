package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NamespaceStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class DeleteKubernetesNamespaceStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger = LoggerFactory.getLogger(DeleteKubernetesNamespaceStep.class);
  private final UUID workspaceId;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final ControlledAzureKubernetesNamespaceResource resource;

  public DeleteKubernetesNamespaceStep(
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
      logger.info("Deleting namespace {}", resource.getKubernetesNamespace());
      coreApiClient.deleteNamespace(
          resource.getKubernetesNamespace(), null, null, null, null, null, null);
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.NOT_FOUND);
    }

    waitForDeletion(coreApiClient);

    return StepResult.getStepResultSuccess();
  }

  private void waitForDeletion(CoreV1Api coreApiClient) throws InterruptedException {
    try {
      RetryUtils.getWithRetry(
          this::isNamespaceGone,
          () -> getNamespaceStatus(coreApiClient),
          Duration.ofMinutes(10),
          Duration.ofSeconds(10),
          0.0,
          Duration.ofSeconds(10));
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          "Error waiting for namespace %s to be deleted"
              .formatted(resource.getKubernetesNamespace()),
          e);
    }
  }

  private boolean isNamespaceGone(Optional<String> namespacePhase) {
    return namespacePhase.isEmpty();
  }

  private Optional<String> getNamespaceStatus(CoreV1Api coreApiClient) {
    try {
      var namespace = coreApiClient.readNamespace(resource.getKubernetesNamespace(), null);
      var phase = Optional.ofNullable(namespace.getStatus()).map(V1NamespaceStatus::getPhase);
      logger.info("Status = {} for azure namespace = {}", phase, resource.getKubernetesNamespace());
      return phase;
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      } else {
        // this is called in a retry loop, so we don't want to throw an exception here
        logger.error(
            "Error reading namespace {} for workspace {}",
            resource.getKubernetesNamespace(),
            workspaceId,
            e);
        return Optional.of("Error checking namespace status");
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
