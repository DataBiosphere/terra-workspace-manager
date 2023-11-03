package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class KubernetesClientProvider {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WorkspaceService workspaceService;

  @Autowired
  public KubernetesClientProvider(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      LandingZoneApiDispatch landingZoneApiDispatch,
      WorkspaceService workspaceService) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.workspaceService = workspaceService;
  }

  @NotNull
  public CoreV1Api createCoreApiClient(AzureCloudContext azureCloudContext, UUID workspaceId) {
    return createCoreApiClient(azureCloudContext, getClusterResource(workspaceId));
  }

  @NotNull
  public CoreV1Api createCoreApiClient(
      AzureCloudContext azureCloudContext, ApiAzureLandingZoneDeployedResource clusterResource) {
    var containerServiceManager =
        crlService.getContainerServiceManager(azureCloudContext, azureConfig);
    return createCoreApiClient(containerServiceManager, azureCloudContext, clusterResource);
  }

  public CoreV1Api createCoreApiClient(
      ContainerServiceManager containerServiceManager,
      AzureCloudContext azureCloudContext,
      ApiAzureLandingZoneDeployedResource clusterResource) {
    KubeConfig kubeConfig =
        loadKubeConfig(
            containerServiceManager,
            azureCloudContext.getAzureResourceGroupId(),
            getResourceName(clusterResource));
    var userToken = kubeConfig.getCredentials().get("token");

    ApiClient client =
        Config.fromToken(kubeConfig.getServer(), userToken)
            .setSslCaCert(
                new ByteArrayInputStream(
                    Base64.getDecoder()
                        .decode(
                            kubeConfig
                                .getCertificateAuthorityData()
                                .getBytes(StandardCharsets.UTF_8))));
    return new CoreV1Api(client);
  }

  @NotNull
  private KubeConfig loadKubeConfig(
      ContainerServiceManager containerServiceManager, String mrgName, String aksClusterName) {
    var rawKubeConfig =
        containerServiceManager
            .kubernetesClusters()
            .manager()
            .serviceClient()
            .getManagedClusters()
            .listClusterAdminCredentials(mrgName, aksClusterName)
            .kubeconfigs()
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No kubeconfig found"));
    var kubeConfig =
        KubeConfig.loadKubeConfig(
            new InputStreamReader(
                new ByteArrayInputStream(rawKubeConfig.value()), StandardCharsets.UTF_8));
    return kubeConfig;
  }

  public Optional<RuntimeException> convertApiException(
      ApiException exception, HttpStatus... okStatuses) {
    var maybeStatusCode = Optional.ofNullable(HttpStatus.resolve(exception.getCode()));
    if (maybeStatusCode.isEmpty()) {
      return Optional.of(
          new RuntimeException("kubernetes api call failed without http status", exception));
    }
    var statusCode = maybeStatusCode.get();
    if (Arrays.asList(okStatuses).contains(statusCode)) {
      // do nothing, this is an ok status code
      return Optional.empty();
    } else if (statusCode.is5xxServerError()) {
      return Optional.of(new RetryException(exception));
    } else {
      return Optional.of(
          new RuntimeException(
              "kubernetes api call failed: %s".formatted(exception.getResponseBody()), exception));
    }
  }

  public StepResult stepResultFromException(ApiException exception, HttpStatus... okStatuses) {
    var maybeException = convertApiException(exception, okStatuses);
    return maybeException
        .map(
            e -> {
              if (e instanceof RetryException) {
                return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
              } else {
                return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
              }
            })
        .orElseGet(StepResult::getStepResultSuccess);
  }

  public ApiAzureLandingZoneDeployedResource getClusterResource(UUID workspaceId) {
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var clusterResource =
        landingZoneApiDispatch
            .getSharedKubernetesCluster(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("No shared cluster found"));
    return clusterResource;
  }
}
