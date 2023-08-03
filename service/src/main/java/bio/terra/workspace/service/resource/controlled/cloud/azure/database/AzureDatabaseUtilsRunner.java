package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Wrapper around <a href="{@docRoot}/azureDatabaseUtils/README.md">AzureDatabaseUtils</a>. Each
 * public method in this class is a wrapper around a command in AzureDatabaseUtils. Every call
 * creates a pod in the default namespace
 */
@Component
public class AzureDatabaseUtilsRunner {
  private static final Logger logger = LoggerFactory.getLogger(AzureDatabaseUtilsRunner.class);
  public static final String POD_FAILED = "Failed";
  public static final String POD_SUCCEEDED = "Succeeded";
  // namespace where we create the pod to create the database
  private static final String aksNamespace = "default";

  private final AzureConfiguration azureConfig;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WorkspaceService workspaceService;
  private final SamService samService;
  private final KubernetesClientProvider kubernetesClientProvider;

  public AzureDatabaseUtilsRunner(
      AzureConfiguration azureConfig,
      LandingZoneApiDispatch landingZoneApiDispatch,
      WorkspaceService workspaceService,
      SamService samService,
      KubernetesClientProvider kubernetesClientProvider) {
    this.azureConfig = azureConfig;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.workspaceService = workspaceService;
    this.samService = samService;
    this.kubernetesClientProvider = kubernetesClientProvider;
  }

  /**
   * Creates a database in the landing zone postgres server and grants the user access to it.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName
   * @param userName
   * @param userOid
   * @param databaseName
   * @throws InterruptedException
   */
  public void createDatabase(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String userName,
      String userOid,
      String databaseName)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name("spring_profiles_active").value("CreateDatabase"),
            new V1EnvVar().name("NEW_DB_USER_NAME").value(userName),
            new V1EnvVar().name("NEW_DB_USER_OID").value(userOid),
            new V1EnvVar().name("NEW_DB_NAME").value(databaseName));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * Creates a database in the landing zone postgres server and creates a role with access to it.
   * The database role cannot be used to login to the database.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName
   * @param databaseName
   * @throws InterruptedException
   */
  public void createDatabaseWithDbRole(
      AzureCloudContext azureCloudContext, UUID workspaceId, String podName, String databaseName)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name("spring_profiles_active").value("CreateDatabaseWithDbRole"),
            new V1EnvVar().name("NEW_DB_NAME").value(databaseName));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * Creates a user in the landing zone postgres server and grants it access to roles associated to
   * each of databaseNames.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName
   * @param userName
   * @param userOid
   * @param databaseNames
   * @throws InterruptedException
   */
  public void createUser(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String userName,
      String userOid,
      Set<String> databaseNames)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name("spring_profiles_active").value("CreateUser"),
            new V1EnvVar().name("NEW_DB_USER_NAME").value(userName),
            new V1EnvVar().name("NEW_DB_USER_OID").value(userOid),
            new V1EnvVar().name("DATABASE_NAMES").value(String.join(",", databaseNames)));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * Deletes a user from the landing zone postgres server thus revoking its access to any databases.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName
   * @param userName
   * @throws InterruptedException
   */
  public void deleteUser(
      AzureCloudContext azureCloudContext, UUID workspaceId, String podName, String userName)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name("spring_profiles_active").value("DeleteUser"),
            new V1EnvVar().name("DB_USER_NAME").value(userName));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * A function that can be used to test connectivity to the landing zone postgres server.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName
   * @param databaseName
   * @throws InterruptedException
   */
  public void testDatabaseConnect(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String namespace,
      String podName,
      String dbServerName,
      String databaseName,
      String ksaName)
      throws InterruptedException {
    List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name("spring_profiles_active").value("TestDatabaseConnect"),
            new V1EnvVar().name("DB_SERVER_NAME").value(dbServerName),
            new V1EnvVar().name("ADMIN_DB_USER_NAME").value(ksaName),
            new V1EnvVar().name("CONNECT_TO_DATABASE").value(databaseName));

    var podDefinition =
        new V1Pod()
            .metadata(
                new V1ObjectMeta()
                    .name(podName)
                    .labels(Map.of("azure.workload.identity/use", "true")))
            .spec(
                new V1PodSpec()
                    .serviceAccountName(ksaName)
                    .restartPolicy("Never")
                    .addContainersItem(
                        new V1Container()
                            .name(podName)
                            .image(azureConfig.getAzureDatabaseUtilImage())
                            .env(envVars)));

    runAzureDatabaseUtils(azureCloudContext, workspaceId, podDefinition, namespace);
  }

  private void runAzureDatabaseUtils(
      AzureCloudContext azureCloudContext, UUID workspaceId, V1Pod podDefinition, String namespace)
      throws InterruptedException {
    var aksApi = kubernetesClientProvider.createCoreApiClient(azureCloudContext, workspaceId);
    // strip underscores to avoid violating azure's naming conventions for pods
    var safePodName = podDefinition.getMetadata().getName();
    try {
      startContainer(aksApi, podDefinition, namespace);
      var finalPhase = waitForContainer(aksApi, safePodName, namespace);
      if (finalPhase.stream().anyMatch(phase -> phase.equals(POD_FAILED))) {
        logger.info("Azure database utils pod failed with phase {}", finalPhase);
        throw new RetryException("Azure database utils pod failed");
      }
    } catch (ApiException e) {
      throw new RetryException(e);
    } finally {
      logPodLogs(
          aksApi,
          safePodName,
          azureConfig.getAzureDatabaseUtilLogsTailLines(),
          workspaceId,
          namespace);
      deleteContainer(aksApi, safePodName, namespace);
    }
  }

  private void deleteContainer(CoreV1Api aksApi, String podName, String namespace) {
    try {
      aksApi.deleteNamespacedPod(podName, namespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      logger.warn("Failed to delete azure database utils pod", e);
    }
  }

  private Optional<String> waitForContainer(CoreV1Api aksApi, String podName, String namespace)
      throws InterruptedException {
    try {
      return RetryUtils.getWithRetry(
          this::isPodDone,
          () -> getPodStatus(aksApi, podName, namespace),
          Duration.ofMinutes(10),
          Duration.ofSeconds(10),
          0.0,
          Duration.ofSeconds(10));
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("timed out waiting for azure database utils container", e);
    }
  }

  private void logPodLogs(
      CoreV1Api aksApi, String podName, Integer tailLines, UUID workspaceId, String namespace) {
    var logContext = Map.of("workspaceId", workspaceId, "podName", podName);
    try {
      var pod = aksApi.readNamespacedPod(podName, namespace, null);
      logger.info("Pod final status: {}", pod.getStatus(), logContext);

      try (InputStream logs =
          new PodLogs(aksApi.getApiClient())
              .streamNamespacedPodLog(
                  namespace,
                  podName,
                  podName,
                  null,
                  Optional.ofNullable(tailLines).orElse(1000),
                  false)) {

        new BufferedReader(new InputStreamReader(logs))
            .lines()
            .forEach(line -> logger.info("pod log line: {}", line, logContext));
      }
    } catch (Exception e) {
      logger.info("failed to get pod logs", logContext, e);
    }
  }

  private Optional<String> getPodStatus(CoreV1Api aksApi, String podName, String namespace) {
    try {
      var status =
          Optional.ofNullable(aksApi.readNamespacedPod(podName, namespace, null).getStatus())
              .map(V1PodStatus::getPhase);
      logger.info("Status = {} for azure database utils pod = {}", status, podName);
      return status;
    } catch (ApiException e) {
      kubernetesClientProvider
          .convertApiException(e)
          .ifPresent(
              (ex) -> {
                throw ex;
              });
      throw new RuntimeException(
          "This should not have happened, convertApiException above should have returned an exception",
          e);
    }
  }

  private boolean isPodDone(Optional<String> podPhase) {
    return podPhase
        .map(phase -> phase.equals(POD_SUCCEEDED) || phase.equals(POD_FAILED))
        .orElse(false);
  }

  private void startContainer(CoreV1Api aksApi, V1Pod podDefinition, String namespace)
      throws ApiException {
    try {
      logger.info("Creating pod {}", podDefinition);
      aksApi.createNamespacedPod(namespace, podDefinition, null, null, null, null);
    } catch (ApiException e) {
      var status = Optional.ofNullable(HttpStatus.resolve(e.getCode()));
      // If the pod already exists, assume this is a retry, monitor the already running pod
      if (status.stream().noneMatch(s -> s == HttpStatus.CONFLICT)) {
        logger.error("Error in azure database utils; response = {}", e.getResponseBody(), e);
        throw e;
      }
    }
  }

  private V1Pod createPodDefinition(UUID workspaceId, String podName, List<V1EnvVar> envVars) {
    var safePodName = podName.replace('_', '-');
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var dbServerName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabase(bearerToken, landingZoneId)
                .orElseThrow(() -> new RuntimeException("No shared database found")));
    var adminDbUserName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabaseAdminIdentity(bearerToken, landingZoneId)
                .orElseThrow(
                    () -> new RuntimeException("No shared database admin identity found")));

    List<V1EnvVar> envVarsWithCommonArgs = new ArrayList<>();
    envVarsWithCommonArgs.add(new V1EnvVar().name("DB_SERVER_NAME").value(dbServerName));
    envVarsWithCommonArgs.add(new V1EnvVar().name("ADMIN_DB_USER_NAME").value(adminDbUserName));
    envVarsWithCommonArgs.addAll(envVars);

    return new V1Pod()
        .metadata(
            new V1ObjectMeta()
                .name(safePodName)
                .labels(Map.of("azure.workload.identity/use", "true")))
        .spec(
            new V1PodSpec()
                .serviceAccountName(adminDbUserName)
                .restartPolicy("Never")
                .addContainersItem(
                    new V1Container()
                        .name(safePodName)
                        .image(azureConfig.getAzureDatabaseUtilImage())
                        .env(envVarsWithCommonArgs)));
  }
}
