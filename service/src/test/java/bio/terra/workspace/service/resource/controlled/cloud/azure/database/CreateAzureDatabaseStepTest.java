package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ManagedIdentityStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Databases;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class CreateAzureDatabaseStepTest {
  private MockitoSession mockito;

  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private CrlService mockCrlService;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private KubernetesClientProvider mockKubernetesClient;
  @Mock private FlightContext mockFlightContext;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private Identity mockIdentity;
  @Mock private ApiAzureLandingZoneDeployedResource mockCluster;
  @Mock private CoreV1Api mockCoreV1Api;
  @Captor private ArgumentCaptor<V1Pod> podCaptor;
  @Mock private ApiAzureLandingZoneDeployedResource mockDatabase;
  @Mock private ApiAzureLandingZoneDeployedResource mockAdminIdentity;
  @Mock private PostgreSqlManager mockPostgreSqlManager;
  @Mock private Databases mockDatabases;
  @Mock private HttpResponse mockHttpResponse;

  private final UUID workspaceId = UUID.randomUUID();
  private final String uamiName = UUID.randomUUID().toString();
  private final ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null);
  private final ControlledAzureDatabaseResource databaseResource =
      ControlledAzureResourceFixtures.makeDefaultControlledAzureDatabaseResourceBuilder(
              creationParameters, workspaceId)
          .build();

  @BeforeEach
  public void setup() {
    // initialize session to start mocking
    mockito =
        Mockito.mockitoSession().initMocks(this).strictness(Strictness.STRICT_STUBS).startMocking();
  }

  @AfterEach
  public void tearDown() {
    mockito.finishMocking();
  }

  @Test
  void testSuccess() throws InterruptedException, ApiException {
    var step = setupStepTest(CreateAzureDatabaseStep.POD_SUCCEEDED);
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    var env =
        podCaptor.getValue().getSpec().getContainers().get(0).getEnv().stream()
            .collect(Collectors.toMap(V1EnvVar::getName, V1EnvVar::getValue));

    assertThat(env.get("DB_SERVER_NAME"), equalTo(mockDatabase.getResourceId()));
    assertThat(env.get("ADMIN_DB_USER_NAME"), equalTo(mockAdminIdentity.getResourceId()));
    assertThat(env.get("NEW_DB_USER_NAME"), equalTo(mockIdentity.name()));
    assertThat(env.get("NEW_DB_USER_OID"), equalTo(mockIdentity.principalId()));
    assertThat(env.get("NEW_DB_NAME"), equalTo(databaseResource.getDatabaseName()));

    assertThat(
        podCaptor.getValue().getSpec().getServiceAccountName(),
        equalTo(mockAdminIdentity.getResourceId()));
  }

  @Test
  void testRetry() throws InterruptedException, ApiException {
    var step = setupStepTest(CreateAzureDatabaseStep.POD_FAILED);
    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testUndo() throws InterruptedException {
    final String databaseServerName = UUID.randomUUID().toString();
    CreateAzureDatabaseStep step = setupUndoTest(databaseServerName, workspaceId, databaseResource);

    doNothing()
        .when(mockDatabases)
        .delete(any(), eq(databaseServerName), eq(databaseResource.getDatabaseName()));

    var result = step.undoStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void testUndoDatabaseDoesNotExist() throws InterruptedException {
    final String databaseServerName = UUID.randomUUID().toString();

    CreateAzureDatabaseStep step = setupUndoTest(databaseServerName, workspaceId, databaseResource);

    HttpStatus httpStatus = HttpStatus.NOT_FOUND;
    doThrow(new ManagementException(httpStatus.name(), mockHttpResponse))
        .when(mockDatabases)
        .delete(any(), eq(databaseServerName), eq(databaseResource.getDatabaseName()));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    var result = step.undoStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @NotNull
  private CreateAzureDatabaseStep setupUndoTest(
      String databaseServerName,
      UUID workspaceId,
      ControlledAzureDatabaseResource databaseResource) {
    createMockFlightContext();

    when(mockCrlService.getPostgreSqlManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockPostgreSqlManager);
    when(mockPostgreSqlManager.databases()).thenReturn(mockDatabases);

    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockDatabase.getResourceId()).thenReturn(databaseServerName);

    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());

    return new CreateAzureDatabaseStep(
        mockAzureConfig,
        mockCrlService,
        databaseResource,
        mockLandingZoneApiDispatch,
        mockSamService,
        mockWorkspaceService,
        workspaceId,
        mockKubernetesClient
    );
  }

  @NotNull
  private CreateAzureDatabaseStep setupStepTest(String podPhase) throws ApiException {
    createMockFlightContext();

    when(mockCrlService.getMsiManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentity.name()).thenReturn(uamiName);
    when(mockIdentity.principalId()).thenReturn(UUID.randomUUID().toString());

    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenReturn(UUID.randomUUID());

    when(mockLandingZoneApiDispatch.getSharedDatabaseAdminIdentity(any(), any()))
        .thenReturn(Optional.of(mockAdminIdentity));
    when(mockAdminIdentity.getResourceId()).thenReturn(UUID.randomUUID().toString());

    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockDatabase.getResourceId()).thenReturn(UUID.randomUUID().toString());

    when(mockLandingZoneApiDispatch.getSharedKubernetesCluster(any(), any()))
        .thenReturn(Optional.of(mockCluster));
    when(mockKubernetesClient.createCoreApiClient(any(), any(), any())).thenReturn(mockCoreV1Api);

    when(mockCoreV1Api.createNamespacedPod(any(), podCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(new V1Pod());
    when(mockCoreV1Api.readNamespacedPod(
            eq(workspaceId + databaseResource.getDatabaseName() + uamiName), any(), any()))
        .thenReturn(new V1Pod().status(new V1PodStatus().phase(podPhase)));
    when(mockCoreV1Api.deleteNamespacedPod(
            eq(workspaceId + databaseResource.getDatabaseName() + uamiName),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(new V1Pod());

    return new CreateAzureDatabaseStep(
        mockAzureConfig,
        mockCrlService,
        databaseResource,
        mockLandingZoneApiDispatch,
        mockSamService,
        mockWorkspaceService,
        workspaceId,
        mockKubernetesClient
    );
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockWorkingMap.get(ManagedIdentityStep.MANAGED_IDENTITY, Identity.class)).thenReturn(mockIdentity);

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(UUID.randomUUID().toString());

    return mockFlightContext;
  }
}
