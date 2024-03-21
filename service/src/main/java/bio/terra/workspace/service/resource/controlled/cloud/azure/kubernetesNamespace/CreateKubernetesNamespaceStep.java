package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.cloudres.azure.resourcemanager.kubernetes.data.CreateKubernetesNamespaceRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.MissingResourceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CreateKubernetesNamespaceStep implements Step {
  private final UUID workspaceId;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final ControlledAzureKubernetesNamespaceResource resource;
  private final CrlService crlService;

  public CreateKubernetesNamespaceStep(
      UUID workspaceId,
      KubernetesClientProvider kubernetesClientProvider,
      ControlledAzureKubernetesNamespaceResource resource,
      CrlService crlService) {
    this.workspaceId = workspaceId;
    this.kubernetesClientProvider = kubernetesClientProvider;
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var clusterResource =
        kubernetesClientProvider
            .getClusterResource(workspaceId)
            .orElseThrow(
                () ->
                    new MissingResourceException(
                        "No shared cluster found for workspace",
                        ApiAzureLandingZoneDeployedResource.class.getSimpleName(),
                        workspaceId.toString()));

    // Record namespace for cleanup in Janitor
    crlService.recordAzureCleanup(
        CreateKubernetesNamespaceRequestData.builder()
            .setNamespaceName(resource.getKubernetesNamespace())
            .setClusterName(getResourceName(clusterResource))
            .setTenantId(azureCloudContext.getAzureTenantId())
            .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
            .build());

    var coreApiClient =
        kubernetesClientProvider.createCoreApiClient(azureCloudContext, clusterResource);

    try {
      var namespace =
          new V1Namespace().metadata(new V1ObjectMeta().name(resource.getKubernetesNamespace()));
      coreApiClient.createNamespace(namespace).execute();
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
        kubernetesClientProvider
            .createCoreApiClient(azureCloudContext, workspaceId)
            .orElseThrow(
                () ->
                    new MissingResourceException(
                        "No shared cluster found for workspace",
                        ApiAzureLandingZoneDeployedResource.class.getSimpleName(),
                        workspaceId.toString()));

    try {
      coreApiClient.deleteNamespace(resource.getKubernetesNamespace()).execute();
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.NOT_FOUND);
    }
    return StepResult.getStepResultSuccess();
  }
}
