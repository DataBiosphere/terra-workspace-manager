package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an Azure Database. Designed to run directly after {@link GetAzureDatabaseStep}. */
public class CreateAzureDatabaseStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureDatabaseStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDatabaseResource resource;

  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;

  public CreateAzureDatabaseStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDatabaseResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch, SamService samService,
      WorkspaceService workspaceService, UUID workspaceId) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var containerServiceManager = ContainerServiceManager.authenticate(getManagedAppCredentials(), getAzureProfile(azureCloudContext));

    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var clusterResource = landingZoneApiDispatch.getSharedKubernetesCluster(bearerToken, landingZoneId).orElseThrow(() -> new RuntimeException("No shared cluster found"));
    var rawKubeConfig = containerServiceManager.kubernetesClusters().manager().serviceClient().getManagedClusters().listClusterUserCredentials(azureCloudContext.getAzureResourceGroupId(), clusterResource.getResourceName()).kubeconfigs().stream().findFirst().orElseThrow(() -> new RuntimeException("No kubeconfig found"));
    var kubeConfig = KubeConfig.loadKubeConfig(new InputStreamReader(new ByteArrayInputStream(rawKubeConfig.value())));
    var userToken = kubeConfig.getCredentials().get("token");
    ApiClient client = Config.fromToken(kubeConfig.getServer(), userToken);
    CoreV1Api api = new CoreV1Api(client);
    V1Pod pod =
        new V1Pod()
            .metadata(new V1ObjectMeta().name("apod"))
            .spec(new V1PodSpec().addContainersItem(new V1Container().name("www").image("nginx")));

//    api.createNamespacedPod("default", pod, null, null, null, null);


    try {

      // TODO

    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.CONFLICT)) {
        logger.info(
            "Azure Database {} in managed resource group {} already exists",
            resource.getDatabaseName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
//    final AzureCloudContext azureCloudContext =
//        context
//            .getWorkingMap()
//            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
//
//
//    try {
//      msiManager.identities()
//          .deleteByResourceGroup(
//              azureCloudContext.getAzureResourceGroupId(), resource.getDatabaseName());
//    } catch (ManagementException e) {
//      // Stairway steps may run multiple times, so we may already have deleted this resource.
//      if (AzureManagementExceptionUtils.isExceptionCode(
//          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
//        logger.info(
//            "Azure Database {} in managed resource group {} already deleted",
//            resource.getDatabaseName(),
//            azureCloudContext.getAzureResourceGroupId());
//        return StepResult.getStepResultSuccess();
//      }
//      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
//    }
    return StepResult.getStepResultSuccess();
  }

  private void foo() throws IOException, ApiException {


    ApiClient client = Config.defaultClient();
    Configuration.setDefaultApiClient(client);

    CoreV1Api api = new CoreV1Api();

    V1Pod pod =
        new V1Pod()
            .metadata(new V1ObjectMeta().name("apod"))
            .spec(new V1PodSpec().addContainersItem(new V1Container().name("www").image("nginx")));

    api.createNamespacedPod("default", pod, null, null, null, null);
  }

  private TokenCredential getManagedAppCredentials() {
    return new ClientSecretCredentialBuilder()
        .clientId(azureConfig.getManagedAppClientId())
        .clientSecret(azureConfig.getManagedAppClientSecret())
        .tenantId(azureConfig.getManagedAppTenantId())
        .build();
  }

  private AzureProfile getAzureProfile(AzureCloudContext azureCloudContext) {
    return new AzureProfile(
        azureCloudContext.getAzureTenantId(),
        azureCloudContext.getAzureSubscriptionId(),
        AzureEnvironment.AZURE);
  }
}
