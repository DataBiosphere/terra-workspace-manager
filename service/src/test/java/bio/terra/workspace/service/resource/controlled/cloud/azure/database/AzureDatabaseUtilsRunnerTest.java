package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@Tag("azure-unit")
public class AzureDatabaseUtilsRunnerTest {
  private MockitoSession mockito;
  private AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private SamService mockSamService;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private CoreV1Api mockCoreV1Api;
  @Captor private ArgumentCaptor<V1Pod> podCaptor;
  @Mock private ApiAzureLandingZoneDeployedResource mockDatabase;
  @Mock private ApiAzureLandingZoneDeployedResource mockAdminIdentity;

  @BeforeEach
  public void setup() {
    // initialize session to start mocking
    mockito =
        Mockito.mockitoSession().initMocks(this).strictness(Strictness.STRICT_STUBS).startMocking();
    azureDatabaseUtilsRunner =
        new AzureDatabaseUtilsRunner(
            mockAzureConfig,
            mockLandingZoneApiDispatch,
            mockWorkspaceService,
            mockSamService,
            mockKubernetesClientProvider);
  }

  @AfterEach
  public void tearDown() {
    mockito.finishMocking();
  }

  @Test
  void testCreateDatabase() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var userOid = UUID.randomUUID().toString();
    var databaseName = "test-database";
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.createDatabase(
        mockAzureCloudContext, workspaceId, podName, userName, userOid, databaseName);

    assertResults(
        Map.of(
            "spring_profiles_active", "CreateDatabase",
            "NEW_DB_USER_NAME", userName,
            "NEW_DB_USER_OID", userOid,
            "NEW_DB_NAME", databaseName));
  }

  @Test
  void testCreateDatabaseWithDbRole() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var databaseName = "test-database";
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.createDatabaseWithDbRole(
        mockAzureCloudContext, workspaceId, podName, databaseName);

    assertResults(
        Map.of("spring_profiles_active", "CreateDatabaseWithDbRole", "NEW_DB_NAME", databaseName));
  }

  @Test
  void testCreateUser() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var userOid = UUID.randomUUID().toString();
    var databaseNames = Set.of("test-database1", "test-database2");
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.createNamespaceRole(
        mockAzureCloudContext, workspaceId, podName, userName, userOid, databaseNames);

    assertResults(
        Map.of(
            "spring_profiles_active",
            "CreateNamespaceRole",
            "NAMESPACE_ROLE",
            userName,
            "MANAGED_IDENTITY_OID",
            userOid,
            "DATABASE_NAMES",
            String.join(",", databaseNames)));
  }

  @Test
  void testDeleteUser() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.deleteNamespaceRole(
        mockAzureCloudContext, workspaceId, podName, userName);

    assertResults(
        Map.of("spring_profiles_active", "DeleteNamespaceRole", "NAMESPACE_ROLE", userName));
  }

  @Test
  void testRetry() throws ApiException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var podName = "test-pod-name";
    var podPhase = AzureDatabaseUtilsRunner.POD_FAILED;

    setupMocks(podName, podPhase);

    assertThrows(
        RetryException.class,
        () ->
            azureDatabaseUtilsRunner.deleteNamespaceRole(
                mockAzureCloudContext, workspaceId, podName, userName));
  }

  private void assertResults(Map<String, String> expectedEnv) {
    var spec = podCaptor.getValue().getSpec();
    var container = spec.getContainers().get(0);
    var env =
        container.getEnv().stream()
            .collect(Collectors.toMap(V1EnvVar::getName, V1EnvVar::getValue));

    var expectedEnvWithCommon = new HashMap<>(expectedEnv);
    expectedEnvWithCommon.putAll(
        Map.of(
            "DB_SERVER_NAME",
            mockDatabase.getResourceId(),
            "ADMIN_DB_USER_NAME",
            mockAdminIdentity.getResourceId()));
    assertThat(env, equalTo(expectedEnvWithCommon));
    assertThat(container.getImage(), equalTo(mockAzureConfig.getAzureDatabaseUtilImage()));
    assertThat(spec.getServiceAccountName(), equalTo(mockAdminIdentity.getResourceId()));
  }

  private void setupMocks(String podName, String podPhase) throws ApiException {
    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenReturn(UUID.randomUUID());

    when(mockLandingZoneApiDispatch.getSharedDatabaseAdminIdentity(any(), any()))
        .thenReturn(Optional.of(mockAdminIdentity));
    when(mockAdminIdentity.getResourceId()).thenReturn(UUID.randomUUID().toString());

    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockDatabase.getResourceId()).thenReturn(UUID.randomUUID().toString());

    when(mockKubernetesClientProvider.createCoreApiClient(any(), any())).thenReturn(mockCoreV1Api);

    when(mockCoreV1Api.createNamespacedPod(any(), podCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(new V1Pod());
    when(mockCoreV1Api.readNamespacedPod(eq(podName), any(), any()))
        .thenReturn(new V1Pod().status(new V1PodStatus().phase(podPhase)));
    when(mockCoreV1Api.deleteNamespacedPod(
            eq(podName), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new V1Pod());
  }
}
