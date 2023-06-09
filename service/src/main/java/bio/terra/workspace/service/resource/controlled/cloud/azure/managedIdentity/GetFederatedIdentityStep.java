package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
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
  public final String k8sNamespace;
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final UUID managedIdentityId;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;
  private final ResourceDao resourceDao;

  public GetFederatedIdentityStep(
      String k8sNamespace,
      AzureConfiguration azureConfig,
      CrlService crlService,
      UUID managedIdentityId,
      KubernetesClientProvider kubernetesClientProvider,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId,
      ResourceDao resourceDao) {
    this.k8sNamespace = k8sNamespace;
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.managedIdentityId = managedIdentityId;
    this.kubernetesClientProvider = kubernetesClientProvider;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    ControlledAzureManagedIdentityResource managedIdentityResource =
        resourceDao
            .getResource(workspaceId, managedIdentityId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);

    var uamiName = managedIdentityResource.getManagedIdentityName();

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);
    var containerServiceManager =
        crlService.getContainerServiceManager(azureCloudContext, azureConfig);

    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var aksCluster =
        landingZoneApiDispatch
            .getSharedKubernetesCluster(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("Shared Kubernetes cluster not found"));

    var aksApi =
        kubernetesClientProvider.createCoreApiClient(
            containerServiceManager,
            azureCloudContext.getAzureResourceGroupId(),
            aksCluster.getResourceName());

    final boolean k8sServiceAccountExists = k8sServiceAccountExists(uamiName, aksApi);
    final boolean federatedIdentityExists =
        federatedIdentityExists(uamiName, azureCloudContext, msiManager);

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
              .get(azureCloudContext.getAzureResourceGroupId(), uamiName, k8sNamespace)
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
      return aksApi.readNamespacedServiceAccount(uamiName, k8sNamespace, null) != null;
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
