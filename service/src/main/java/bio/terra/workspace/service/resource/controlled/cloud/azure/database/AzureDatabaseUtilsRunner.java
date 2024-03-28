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
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Wrapper around <a
 * href="https://github.com/DataBiosphere/terra-workspace-manager/tree/main/azureDatabaseUtils">AzureDatabaseUtils</a>.
 * Each public method in this class is a wrapper around a command in AzureDatabaseUtils. Every call
 * creates a pod in the default namespace, waits for it to complete then deletes the pod. This
 * pattern of launching a pod to run a command is used because the Azure database server is only
 * accessible from the Landing Zone VNet and only by managed identities in the Landing Zone.
 * Commands are designed to work within Stairway flights. The commands themselves are idempotent.
 * Given the same pod name, they will not create a new pod if one already exists with that name and
 * monitor the existing pod.
 */
@Component
public class AzureDatabaseUtilsRunner {

  private static final Logger logger = LoggerFactory.getLogger(AzureDatabaseUtilsRunner.class);
  public static final String POD_FAILED = "Failed";
  public static final String POD_SUCCEEDED = "Succeeded";
  private static final String aksNamespace = "default";

  public static final String COMMAND_CREATE_NAMESPACE_ROLE = "CreateNamespaceRole";
  public static final String COMMAND_CREATE_DATABASE_WITH_DB_ROLE = "CreateDatabaseWithDbRole";
  public static final String COMMAND_PGDUMP_DATABASE = "PgDumpDatabase";
  public static final String COMMAND_PGRESTORE_DATABASE = "PgRestoreDatabase";
  public static final String COMMAND_DELETE_NAMESPACE_ROLE = "DeleteNamespaceRole";
  public static final String COMMAND_REVOKE_NAMESPACE_ROLE_ACCESS = "RevokeNamespaceRoleAccess";
  public static final String COMMAND_RESTORE_NAMESPACE_ROLE_ACCESS = "RestoreNamespaceRoleAccess";
  public static final String COMMAND_TEST_DATABASE_CONNECT = "TestDatabaseConnect";
  public static final String COMMAND_CREATE_DATABASE = "CreateDatabase";

  public static final String PARAM_SPRING_PROFILES_ACTIVE = "spring_profiles_active";
  public static final String PARAM_NAMESPACE_ROLE = "NAMESPACE_ROLE";
  public static final String PARAM_MANAGED_IDENTITY_OID = "MANAGED_IDENTITY_OID";
  public static final String PARAM_DATABASE_NAMES = "DATABASE_NAMES";
  public static final String PARAM_NEW_DB_NAME = "NEW_DB_NAME";
  public static final String PARAM_DB_SERVER_NAME = "DB_SERVER_NAME";
  public static final String PARAM_ADMIN_DB_USER_NAME = "ADMIN_DB_USER_NAME";
  public static final String PARAM_CONNECT_TO_DATABASE = "CONNECT_TO_DATABASE";
  public static final String PARAM_NEW_DB_USER_NAME = "NEW_DB_USER_NAME";
  public static final String PARAM_NEW_DB_USER_OID = "NEW_DB_USER_OID";

  // Workflow cloning - TODO: which params can be reused?
  public static final String PARAM_BLOB_FILE_NAME = "BLOB_FILE_NAME";
  public static final String PARAM_DEST_WORKSPACE_ID = "DEST_WORKSPACE_ID";
  public static final String PARAM_BLOB_CONTAINER_NAME = "BLOB_CONTAINER_NAME";
  public static final String PARAM_ENCRYPTION_KEY = "ENCRYPTION_KEY";
  public static final String PARAM_BLOB_CONTAINER_URL_AUTHENTICATED =
      "BLOB_CONTAINER_URL_AUTHENTICATED";

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
   * Creates a database in the landing zone postgres server and creates a role with access to it.
   * The database role cannot be used to login to the database.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
   * @param databaseName name of the database to create, a role with the same name will be created
   *     with full privileges on the database
   * @throws InterruptedException
   */
  public void createDatabaseWithDbRole(
      AzureCloudContext azureCloudContext, UUID workspaceId, String podName, String databaseName)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar()
                .name(PARAM_SPRING_PROFILES_ACTIVE)
                .value(COMMAND_CREATE_DATABASE_WITH_DB_ROLE),
            new V1EnvVar().name(PARAM_NEW_DB_NAME).value(databaseName));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  public void pgDumpDatabase(
      AzureCloudContext azureCloudContext,
      UUID sourceWorkspaceId,
      String podName,
      String sourceDbName,
      String dbServerName,
      String dbUserName,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated,
      String encryptionKey)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name(PARAM_SPRING_PROFILES_ACTIVE).value(COMMAND_PGDUMP_DATABASE),
            new V1EnvVar().name(PARAM_CONNECT_TO_DATABASE).value(sourceDbName),
            new V1EnvVar().name(PARAM_DB_SERVER_NAME).value(dbServerName),
            new V1EnvVar().name(PARAM_ADMIN_DB_USER_NAME).value(dbUserName),
            new V1EnvVar().name(PARAM_BLOB_FILE_NAME).value(blobFileName),
            new V1EnvVar().name(PARAM_BLOB_CONTAINER_NAME).value(blobContainerName));

    final Map<String, String> secretStringData =
        Map.ofEntries(
            Map.entry(PARAM_BLOB_CONTAINER_URL_AUTHENTICATED, blobContainerUrlAuthenticated),
            Map.entry(PARAM_ENCRYPTION_KEY, encryptionKey));

    runAzureDatabaseUtils(
        azureCloudContext,
        sourceWorkspaceId,
        createPodDefinition(sourceWorkspaceId, podName, envVars, secretStringData),
        secretStringData,
        aksNamespace);
  }

  public void pgRestoreDatabase(
      AzureCloudContext azureCloudContext,
      UUID targetWorkspaceId,
      String podName,
      String targetDbName,
      String dbServerName,
      String dbUserName,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated,
      String encryptionKey)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name(PARAM_SPRING_PROFILES_ACTIVE).value(COMMAND_PGRESTORE_DATABASE),
            new V1EnvVar().name(PARAM_CONNECT_TO_DATABASE).value(targetDbName),
            new V1EnvVar().name(PARAM_DB_SERVER_NAME).value(dbServerName),
            new V1EnvVar().name(PARAM_ADMIN_DB_USER_NAME).value(dbUserName),
            new V1EnvVar().name(PARAM_BLOB_FILE_NAME).value(blobFileName),
            new V1EnvVar().name(PARAM_BLOB_CONTAINER_NAME).value(blobContainerName));

    final Map<String, String> secretStringData =
        Map.ofEntries(
            Map.entry(PARAM_BLOB_CONTAINER_URL_AUTHENTICATED, blobContainerUrlAuthenticated),
            Map.entry(PARAM_ENCRYPTION_KEY, encryptionKey));

    runAzureDatabaseUtils(
        azureCloudContext,
        targetWorkspaceId,
        createPodDefinition(targetWorkspaceId, podName, envVars, secretStringData),
        secretStringData,
        aksNamespace);
  }

  /**
   * Creates a role for a namespace in the landing zone postgres server and grants it access to the
   * role associated to each of databaseNames.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
   * @param namespaceRoleName the name of the role to create
   * @param managedIdentityOid the object (principal) id of the managed identity to associate to the
   *     role
   * @param databaseNames the names of the databases to grant access to. The role created will be
   *     granted each associated database role.
   * @throws InterruptedException
   */
  public void createNamespaceRole(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String namespaceRoleName,
      String managedIdentityOid,
      Set<String> databaseNames)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name(PARAM_SPRING_PROFILES_ACTIVE).value(COMMAND_CREATE_NAMESPACE_ROLE),
            new V1EnvVar().name(PARAM_NAMESPACE_ROLE).value(namespaceRoleName),
            new V1EnvVar().name(PARAM_MANAGED_IDENTITY_OID).value(managedIdentityOid),
            new V1EnvVar().name(PARAM_DATABASE_NAMES).value(String.join(",", databaseNames)));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * Deletes a namespace role from the landing zone postgres server.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
   * @param namespaceRoleName to delete
   * @throws InterruptedException
   */
  public void deleteNamespaceRole(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String namespaceRoleName)
      throws InterruptedException {
    namespaceRoleCommand(
        azureCloudContext, workspaceId, podName, namespaceRoleName, COMMAND_DELETE_NAMESPACE_ROLE);
  }

  /**
   * Revokes a namespace role's ability to login.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
   * @param namespaceRoleName to revoke
   * @throws InterruptedException
   */
  public void revokeNamespaceRoleAccess(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String namespaceRoleName)
      throws InterruptedException {
    namespaceRoleCommand(
        azureCloudContext,
        workspaceId,
        podName,
        namespaceRoleName,
        COMMAND_REVOKE_NAMESPACE_ROLE_ACCESS);
  }

  /**
   * Restores a namespace role's ability to login.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
   * @param namespaceRoleName to restore
   * @throws InterruptedException
   */
  public void restoreNamespaceRoleAccess(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String namespaceRoleName)
      throws InterruptedException {
    namespaceRoleCommand(
        azureCloudContext,
        workspaceId,
        podName,
        namespaceRoleName,
        COMMAND_RESTORE_NAMESPACE_ROLE_ACCESS);
  }

  /** Runs a command that takes a namespace role name as a parameter. */
  private void namespaceRoleCommand(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      String podName,
      String namespaceRoleName,
      String command)
      throws InterruptedException {
    final List<V1EnvVar> envVars =
        List.of(
            new V1EnvVar().name(PARAM_SPRING_PROFILES_ACTIVE).value(command),
            new V1EnvVar().name(PARAM_NAMESPACE_ROLE).value(namespaceRoleName));
    runAzureDatabaseUtils(
        azureCloudContext,
        workspaceId,
        createPodDefinition(workspaceId, podName, envVars),
        aksNamespace);
  }

  /**
   * A function that can be used to test connectivity to the landing zone postgres server. This is
   * different from the other commands as it runs as a specified user rather than the database
   * server admin and runs in a specified namespace rather than the default namespace, just like an
   * app would run.
   *
   * @param azureCloudContext
   * @param workspaceId
   * @param podName name of the pod created to run the command, should be unique within the LZ and
   *     stable across stairway retries
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
            new V1EnvVar().name(PARAM_SPRING_PROFILES_ACTIVE).value(COMMAND_TEST_DATABASE_CONNECT),
            new V1EnvVar().name(PARAM_DB_SERVER_NAME).value(dbServerName),
            new V1EnvVar().name(PARAM_ADMIN_DB_USER_NAME).value(ksaName),
            new V1EnvVar().name(PARAM_CONNECT_TO_DATABASE).value(databaseName));

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
    runAzureDatabaseUtils(
        azureCloudContext, workspaceId, podDefinition, new HashMap<>(), namespace);
  }

  private void runAzureDatabaseUtils(
      AzureCloudContext azureCloudContext,
      UUID workspaceId,
      V1Pod podDefinition,
      Map<String, String> secretStringData,
      String namespace)
      throws InterruptedException {
    var aksApi =
        kubernetesClientProvider
            .createCoreApiClient(azureCloudContext, workspaceId)
            .orElseThrow(() -> new RuntimeException("No shared cluster found"));

    // strip underscores to avoid violating azure's naming conventions for pods
    var safePodName = podDefinition.getMetadata().getName();
    if (!getPodDefinitionEnvVarsWithSecretRefs(podDefinition).isEmpty()
        && secretStringData.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "definition of pod %s contains env vars that refer to secrets, but no secret data was provided.",
              safePodName));
    }

    try {
      createSecret(
          aksApi,
          secretStringData,
          safePodName, // give the secret the same name as the pod
          namespace);
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
      deleteSecret(aksApi, secretStringData, safePodName, namespace);
    }
  }

  private void createSecret(
      CoreV1Api aksApi, Map<String, String> secretStringData, String secretName, String namespace)
      throws ApiException {

    if (!secretStringData.isEmpty()) {
      final V1Secret secretDefinition =
          new V1Secret().metadata(new V1ObjectMeta().name(secretName));
      secretStringData.forEach(secretDefinition::putStringDataItem);

      try {
        logger.info("Creating secret: {}", secretDefinition.getMetadata());
        aksApi.createNamespacedSecret(namespace, secretDefinition).execute();
      } catch (ApiException e) {
        var status = Optional.ofNullable(HttpStatus.resolve(e.getCode()));
        // If the secret already exists, assume this is a retry.
        if (status.stream().noneMatch(s -> s == HttpStatus.CONFLICT)) {
          logger.error("Error in azure database utils; response = {}", e.getResponseBody(), e);
          throw e;
        }
      }
    } else {
      logger.info("Skipping secret creation; no data provided.");
    }
  }

  private void deleteSecret(
      CoreV1Api aksApi, Map<String, String> secretStringData, String secretName, String namespace) {
    if (!secretStringData.isEmpty()) {
      try {
        aksApi.deleteNamespacedSecret(secretName, namespace).execute();
      } catch (ApiException e) {
        logger.warn("Failed to delete azure database utils secret", e);
      }
    }
  }

  private void deleteContainer(CoreV1Api aksApi, String podName, String namespace) {
    try {
      aksApi.deleteNamespacedPod(podName, namespace).execute();
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
      throw new RuntimeException("exception waiting for azure database utils container", e);
    }
  }

  private void logPodLogs(
      CoreV1Api aksApi, String podName, Integer tailLines, UUID workspaceId, String namespace) {
    var logContext = Map.of("workspaceId", workspaceId, "podName", podName);
    try {
      var pod = aksApi.readNamespacedPod(podName, namespace).execute();
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
          Optional.ofNullable(aksApi.readNamespacedPod(podName, namespace).execute().getStatus())
              .map(V1PodStatus::getPhase);
      logger.info("Status = {} for azure database utils pod = {}", status, podName);
      return status;
    } catch (ApiException e) {
      // this is called in a retry loop, so we don't want to throw an exception here
      logger.error("Error checking azure database utils pod {} status", podName, e);
      return Optional.of("Error checking status");
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
      aksApi.createNamespacedPod(namespace, podDefinition).execute();
    } catch (ApiException e) {
      var status = Optional.ofNullable(HttpStatus.resolve(e.getCode()));
      // If the pod already exists, assume this is a retry, monitor the already running pod
      if (status.stream().noneMatch(s -> s == HttpStatus.CONFLICT)) {
        logger.error("Error in azure database utils; response = {}", e.getResponseBody(), e);
        throw e;
      }
    }
  }

  private V1Pod createPodDefinition(
      UUID workspaceId,
      String podName,
      List<V1EnvVar> envVars,
      Map<String, String> secretStringData) {

    List<V1EnvVar> secretEnvVars = new ArrayList<>();
    var safePodName = k8sSafeName(podName);
    secretStringData
        .keySet()
        .forEach(
            k -> {
              var envVarSource =
                  new V1EnvVarSource()
                      .secretKeyRef(
                          new V1SecretKeySelector()
                              .name(safePodName) // give the secret the same name as the pod
                              .key(k));
              secretEnvVars.add(new V1EnvVar().name(k).valueFrom(envVarSource));
            });

    final List<V1EnvVar> envVarsWithSecrets =
        Stream.concat(envVars.stream(), secretEnvVars.stream()).toList();

    return createPodDefinition(workspaceId, podName, envVarsWithSecrets);
  }

  private V1Pod createPodDefinition(UUID workspaceId, String podName, List<V1EnvVar> envVars) {
    var safePodName = k8sSafeName(podName);
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var dbServerName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabase(bearerToken, landingZoneId)
                .orElseThrow(() -> new IllegalStateException("No shared database found")));
    var adminDbUserName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabaseAdminIdentity(bearerToken, landingZoneId)
                .orElseThrow(
                    () -> new IllegalStateException("No shared database admin identity found")));

    List<V1EnvVar> envVarsWithCommonArgs = new ArrayList<>();
    envVarsWithCommonArgs.add(new V1EnvVar().name(PARAM_DB_SERVER_NAME).value(dbServerName));
    envVarsWithCommonArgs.add(new V1EnvVar().name(PARAM_ADMIN_DB_USER_NAME).value(adminDbUserName));
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
                        .imagePullPolicy("Always")
                        .env(envVarsWithCommonArgs)));
  }

  private List<V1EnvVar> getPodDefinitionEnvVarsWithSecretRefs(V1Pod podDefinition) {
    var container = podDefinition.getSpec().getContainers().get(0);
    return container.getEnv().stream()
        .filter(e -> e.getValueFrom() != null && e.getValueFrom().getSecretKeyRef() != null)
        .toList();
  }

  private String k8sSafeName(String name) {
    return name.replace('_', '-').toLowerCase();
  }
}
