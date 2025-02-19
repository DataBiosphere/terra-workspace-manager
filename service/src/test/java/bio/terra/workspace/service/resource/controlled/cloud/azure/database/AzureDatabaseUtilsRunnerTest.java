package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.AzureEnvironment;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@Tag("azure-unit")
public class AzureDatabaseUtilsRunnerTest extends BaseMockitoStrictStubbingTest {
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
  public void createAzureDatabaseUtilsRunner() {
    when(mockAzureConfig.getAzureEnvironment()).thenReturn(AzureEnvironment.AZURE);
    azureDatabaseUtilsRunner =
        new AzureDatabaseUtilsRunner(
            mockAzureConfig,
            mockLandingZoneApiDispatch,
            mockWorkspaceService,
            mockSamService,
            mockKubernetesClientProvider);
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
  void testRevokeNamespaceRoleAccess() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.revokeNamespaceRoleAccess(
        mockAzureCloudContext, workspaceId, podName, userName);

    assertResults(
        Map.of("spring_profiles_active", "RevokeNamespaceRoleAccess", "NAMESPACE_ROLE", userName));
  }

  @Test
  void testRestoreNamespaceRoleAccess() throws ApiException, InterruptedException {
    final UUID workspaceId = UUID.randomUUID();
    var userName = "test-user";
    var podName = "test-pod-name";

    setupMocks(podName, AzureDatabaseUtilsRunner.POD_SUCCEEDED);

    azureDatabaseUtilsRunner.restoreNamespaceRoleAccess(
        mockAzureCloudContext, workspaceId, podName, userName);

    assertResults(
        Map.of("spring_profiles_active", "RestoreNamespaceRoleAccess", "NAMESPACE_ROLE", userName));
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

    when(mockKubernetesClientProvider.createCoreApiClient(
            any(AzureCloudContext.class), any(UUID.class)))
        .thenReturn(Optional.of(mockCoreV1Api));

    var createRequest = mock(CoreV1Api.APIcreateNamespacedPodRequest.class);
    when(mockCoreV1Api.createNamespacedPod(any(), podCaptor.capture())).thenReturn(createRequest);
    when(createRequest.execute()).thenReturn(new V1Pod());

    var readRequest = mock(CoreV1Api.APIreadNamespacedPodRequest.class);
    when(mockCoreV1Api.readNamespacedPod(eq(podName), any())).thenReturn(readRequest);
    when(readRequest.execute()).thenReturn(new V1Pod().status(new V1PodStatus().phase(podPhase)));

    var deleteRequest = mock(CoreV1Api.APIdeleteNamespacedPodRequest.class);
    when(mockCoreV1Api.deleteNamespacedPod(eq(podName), any())).thenReturn(deleteRequest);
    when(deleteRequest.execute()).thenReturn(new V1Pod());
  }
}
