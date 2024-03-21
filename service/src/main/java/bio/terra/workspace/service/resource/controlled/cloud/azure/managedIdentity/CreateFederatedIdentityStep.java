package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;
import static bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep.FEDERATED_IDENTITY_EXISTS;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.fluent.models.FederatedIdentityCredentialInner;
import com.google.common.annotations.VisibleForTesting;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateFederatedIdentityStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateFederatedIdentityStep.class);
  public final String k8sNamespace;
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final KubernetesClientProvider kubernetesClientProvider;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;
  private final String ksaName;

  public CreateFederatedIdentityStep(
      String k8sNamespace,
      AzureConfiguration azureConfig,
      CrlService crlService,
      KubernetesClientProvider kubernetesClientProvider,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId,
      String ksaName) {
    this.k8sNamespace = k8sNamespace;
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.kubernetesClientProvider = kubernetesClientProvider;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
    this.ksaName = Objects.requireNonNull(ksaName);
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (FlightUtils.getRequired(
        context.getWorkingMap(), FEDERATED_IDENTITY_EXISTS, Boolean.class)) {
      logger.info("Federated identity already exists");
      return StepResult.getStepResultSuccess();
    }

    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());

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
            containerServiceManager, azureCloudContext, aksCluster);

    var oidcIssuer =
        Optional.ofNullable(
                containerServiceManager
                    .kubernetesClusters()
                    .getByResourceGroup(
                        azureCloudContext.getAzureResourceGroupId(), getResourceName(aksCluster))
                    .innerModel()
                    .oidcIssuerProfile()
                    .issuerUrl())
            .orElseThrow(() -> new RuntimeException("OIDC issuer not found"));
    var uamiClientId = GetManagedIdentityStep.getManagedIdentityClientId(context);

    // the code above was lookup and setup, now we are ready to create the federated identity and
    // k8s service account
    return createFederatedIdentityAndK8sServiceAccount(
        GetManagedIdentityStep.getManagedIdentityName(context),
        azureCloudContext,
        msiManager,
        aksApi,
        oidcIssuer,
        uamiClientId);
  }

  @VisibleForTesting
  StepResult createFederatedIdentityAndK8sServiceAccount(
      String uamiName,
      AzureCloudContext azureCloudContext,
      MsiManager msiManager,
      CoreV1Api aksApi,
      String oidcIssuer,
      String uamiClientId) {
    createOrUpdateFederatedCredentials(msiManager, azureCloudContext, uamiName, oidcIssuer);
    try {
      createK8sServiceAccount(aksApi, uamiClientId);
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.CONFLICT.value()) {
        logger.info("K8s service account already exists");
      } else {
        logger.info("Failed to create k8s service account", e);
        if (e.getCode() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
        } else {
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
        }
      }
    }

    return StepResult.getStepResultSuccess();
  }

  private void createK8sServiceAccount(CoreV1Api aksApi, String uamiClientId) throws ApiException {
    var k8sServiceAccount =
        new V1ServiceAccount()
            .metadata(
                new V1ObjectMeta()
                    .annotations(Map.of("azure.workload.identity/client-id", uamiClientId))
                    .name(ksaName)
                    .namespace(k8sNamespace));

    aksApi.createNamespacedServiceAccount(k8sNamespace, k8sServiceAccount).execute();
  }

  private void deleteK8sServiceAccount(CoreV1Api aksApi, String k8sNamespace) throws ApiException {
    aksApi
        .deleteNamespacedServiceAccount(ksaName, k8sNamespace)
        .body(new V1DeleteOptions())
        .execute();
  }

  private void createOrUpdateFederatedCredentials(
      MsiManager msiManager, AzureCloudContext azureContext, String uamiName, String oidcIssuer) {
    msiManager
        .identities()
        .manager()
        .serviceClient()
        .getFederatedIdentityCredentials()
        .createOrUpdate(
            azureContext.getAzureResourceGroupId(),
            uamiName,
            k8sNamespace, // name of federated identity is k8sNamespace, we can have 1 per namespace
            new FederatedIdentityCredentialInner()
                .withIssuer(oidcIssuer)
                .withAudiences(List.of("api://AzureADTokenExchange"))
                .withSubject(String.format("system:serviceaccount:%s:%s", k8sNamespace, ksaName)));
  }

  private void deleteFederatedCredentials(MsiManager msiManager, String mrgName, String uamiName) {
    msiManager
        .identities()
        .manager()
        .serviceClient()
        .getFederatedIdentityCredentials()
        .delete(mrgName, uamiName, k8sNamespace);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    if (FlightUtils.getRequired(
        context.getWorkingMap(), FEDERATED_IDENTITY_EXISTS, Boolean.class)) {
      logger.info("Federated identity already exists");
      return StepResult.getStepResultSuccess();
    }

    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
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

    landingZoneApiDispatch
        .getSharedKubernetesCluster(bearerToken, landingZoneId)
        .ifPresent(
            aksCluster -> {
              try {
                var aksApi =
                    kubernetesClientProvider.createCoreApiClient(
                        containerServiceManager, azureCloudContext, aksCluster);
                deleteK8sServiceAccount(aksApi, k8sNamespace);
              } catch (ApiException e) {
                logger.info("Failed to delete k8s service account", e);
              }
            });

    deleteFederatedCredentials(
        msiManager,
        azureCloudContext.getAzureResourceGroupId(),
        GetManagedIdentityStep.getManagedIdentityName(context));
    return StepResult.getStepResultSuccess();
  }
}
