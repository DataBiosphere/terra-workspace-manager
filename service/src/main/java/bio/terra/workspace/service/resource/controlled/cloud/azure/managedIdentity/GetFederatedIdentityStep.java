package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.MsiManager;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class GetFederatedIdentityStep implements Step {
  public static final String FEDERATED_IDENTITY_EXISTS = "FEDERATED_IDENTITY_EXISTS";
  private static final Logger logger = LoggerFactory.getLogger(GetFederatedIdentityStep.class);
  private final String federatedCredentialName;
  private final String k8sServiceAccountName;
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final UUID workspaceId;

  public GetFederatedIdentityStep(
      String federatedCredentialName,
      String k8sServiceAccountName,
      AzureConfiguration azureConfig,
      CrlService crlService,
      KubernetesClientProvider kubernetesClientProvider,
      UUID workspaceId) {
    this.federatedCredentialName = federatedCredentialName;
    this.k8sServiceAccountName = k8sServiceAccountName;
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.kubernetesClientProvider = kubernetesClientProvider;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    var aksApi =
        kubernetesClientProvider
            .createCoreApiClient(azureCloudContext, workspaceId)
            .orElseThrow(() -> new RuntimeException("No shared cluster found"));

    final boolean k8sServiceAccountExists =
        k8sServiceAccountExists(GetManagedIdentityStep.getManagedIdentityName(context), aksApi);
    final boolean federatedIdentityExists =
        federatedIdentityExists(
            GetManagedIdentityStep.getManagedIdentityName(context), azureCloudContext, msiManager);

    // If both the k8s service account and federated identity exist, the next step will skip
    // creating them.
    // If only one of the k8s service account or federated identity exist, the setup is not complete
    // and the next step will create the missing resource or delete the existing resource on undo.
    context
        .getWorkingMap()
        .put(FEDERATED_IDENTITY_EXISTS, k8sServiceAccountExists && federatedIdentityExists);

    return StepResult.getStepResultSuccess();
  }

  private boolean federatedIdentityExists(
      String uamiName, AzureCloudContext azureCloudContext, MsiManager msiManager) {
    try {
      return msiManager
              .identities()
              .manager()
              .serviceClient()
              .getFederatedIdentityCredentials()
              .get(azureCloudContext.getAzureResourceGroupId(), uamiName, federatedCredentialName)
          != null;
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        return false;
      } else {
        throw new RetryException(e);
      }
    }
  }

  private boolean k8sServiceAccountExists(String uamiName, CoreV1Api aksApi) {
    try {
      return aksApi.readNamespacedServiceAccount(uamiName, k8sServiceAccountName).execute() != null;
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
        return false;
      } else {
        throw new RetryException(e);
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
