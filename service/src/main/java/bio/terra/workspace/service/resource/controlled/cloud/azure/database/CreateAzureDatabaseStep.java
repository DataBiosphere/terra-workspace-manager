package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Creates an Azure Database. Designed to run directly after {@link GetAzureDatabaseStep}. */
public class CreateAzureDatabaseStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureDatabaseStep.class);
  public static final String POD_FAILED = "Failed";
  public static final String POD_SUCCEEDED = "Succeeded";
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDatabaseResource resource;

  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;
  private final KubernetesClientProvider kubernetesClientProvider;

  // namespace where we create the pod to create the database
  private final String aksNamespace = "default";

  public CreateAzureDatabaseStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureDatabaseResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId,
      KubernetesClientProvider kubernetesClientProvider) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
    this.kubernetesClientProvider = kubernetesClientProvider;
  }

  private String getPodName(String newDbUserName) {
    return (this.workspaceId.toString() + this.resource.getDatabaseName() + newDbUserName)
        .replace('_', '-');
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var containerServiceManager =
        crlService.getContainerServiceManager(azureCloudContext, azureConfig);

    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var clusterResource =
        landingZoneApiDispatch
            .getSharedKubernetesCluster(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("No shared cluster found"));

    var aksApi =
        kubernetesClientProvider.createCoreApiClient(
            containerServiceManager, azureCloudContext.getAzureResourceGroupId(), clusterResource);
    var podName = getPodName(GetManagedIdentityStep.getManagedIdentityName(context));
    try {
      startCreateDatabaseContainer(
          aksApi,
          bearerToken,
          landingZoneId,
          GetManagedIdentityStep.getManagedIdentityName(context),
          GetManagedIdentityStep.getManagedIdentityPrincipalId(context),
          podName);
      var finalPhase = waitForCreateDatabaseContainer(aksApi, podName);
      if (finalPhase.stream().anyMatch(phase -> phase.equals(POD_FAILED))) {
        logger.info("Create database pod failed with phase {}", finalPhase);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (ApiException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } finally {
      deleteCreateDatabaseContainer(aksApi, podName);
    }

    return StepResult.getStepResultSuccess();
  }

  private void deleteCreateDatabaseContainer(CoreV1Api aksApi, String podName) {
    try {
      aksApi.deleteNamespacedPod(podName, aksNamespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      logger.warn("Failed to delete create database pod", e);
    }
  }

  private Optional<String> waitForCreateDatabaseContainer(CoreV1Api aksApi, String podName)
      throws Exception {
    return RetryUtils.getWithRetry(
        this::isPodDone,
        () -> getPodStatus(aksApi, podName),
        Duration.ofMinutes(10),
        Duration.ofSeconds(10),
        0.0,
        Duration.ofSeconds(10));
  }

  private Optional<String> getPodStatus(CoreV1Api aksApi, String podName) throws ApiException {
    var status =
        Optional.ofNullable(aksApi.readNamespacedPod(podName, aksNamespace, null).getStatus())
            .map(V1PodStatus::getPhase);
    logger.debug("Status = {} for database creation pod = {}", status, podName);
    return status;
  }

  private boolean isPodDone(Optional<String> podPhase) {
    return podPhase
        .map(phase -> phase.equals(POD_SUCCEEDED) || phase.equals(POD_FAILED))
        .orElse(false);
  }

  private void startCreateDatabaseContainer(
      CoreV1Api aksApi,
      BearerToken bearerToken,
      UUID landingZoneId,
      String newDbUserName,
      String newDbUserOid,
      String podName)
      throws ApiException {
    try {
      String dbServerName =
          getResourceName(
              landingZoneApiDispatch
                  .getSharedDatabase(bearerToken, landingZoneId)
                  .orElseThrow(() -> new RuntimeException("No shared database found")));
      String adminDbUserName =
          getResourceName(
              landingZoneApiDispatch
                  .getSharedDatabaseAdminIdentity(bearerToken, landingZoneId)
                  .orElseThrow(
                      () -> new RuntimeException("No shared database admin identity found")));
      V1Pod pod =
          new V1Pod()
              .metadata(
                  new V1ObjectMeta()
                      .name(podName)
                      .labels(Map.of("azure.workload.identity/use", "true")))
              .spec(
                  new V1PodSpec()
                      .serviceAccountName(adminDbUserName)
                      .restartPolicy("Never")
                      .addContainersItem(
                          new V1Container()
                              .name("createdb")
                              .image(azureConfig.getAzureDatabaseUtilImage())
                              .env(
                                  List.of(
                                      new V1EnvVar()
                                          .name("spring_profiles_active")
                                          .value("CreateDatabase"),
                                      new V1EnvVar().name("DB_SERVER_NAME").value(dbServerName),
                                      new V1EnvVar()
                                          .name("ADMIN_DB_USER_NAME")
                                          .value(adminDbUserName),
                                      new V1EnvVar().name("NEW_DB_USER_NAME").value(newDbUserName),
                                      new V1EnvVar().name("NEW_DB_USER_OID").value(newDbUserOid),
                                      new V1EnvVar()
                                          .name("NEW_DB_NAME")
                                          .value(resource.getDatabaseName())))));

      aksApi.createNamespacedPod(aksNamespace, pod, null, null, null, null);

    } catch (ApiException e) {
      logger.error("Error creating azure database; response = {}", e.getResponseBody(), e);
      var status = Optional.ofNullable(HttpStatus.resolve(e.getCode()));
      // If the pod already exists, assume this is a retry, monitor the already running pod
      if (status.stream().noneMatch(s -> s == HttpStatus.CONFLICT)) {
        throw e;
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var postgresManager = crlService.getPostgreSqlManager(azureCloudContext, azureConfig);
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var databaseResource =
        landingZoneApiDispatch
            .getSharedDatabase(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("No shared database found"));
    try {
      logger.info(
          "Attempting to delete database {} in server {} of resource group {}",
          getResourceName(databaseResource),
          getResourceName(databaseResource),
          azureCloudContext.getAzureResourceGroupId());

      postgresManager
          .databases()
          .delete(
              azureCloudContext.getAzureResourceGroupId(),
              getResourceName(databaseResource),
              resource.getDatabaseName());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == 404) {
        logger.info(
            "Database {} in server {} of resource group {} not found",
            getResourceName(databaseResource),
            getResourceName(databaseResource),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      } else {
        throw e;
      }
    }
  }
}
