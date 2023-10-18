package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep.FEDERATED_IDENTITY_EXISTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.fluent.FederatedIdentityCredentialsClient;
import com.azure.resourcemanager.msi.fluent.ManagedServiceIdentityClient;
import com.azure.resourcemanager.msi.fluent.models.FederatedIdentityCredentialInner;
import com.azure.resourcemanager.msi.models.Identities;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class CreateFederatedIdentityStepTest extends BaseMockitoStrictStubbingTest {
  private final String k8sNamespace = UUID.randomUUID().toString();
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private CrlService mockCrlService;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;
  private final UUID workspaceId = UUID.randomUUID();
  @Mock private AzureCloudContext mockAzureCloudContext;
  private final ControlledAzureManagedIdentityResource identityResource =
      ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
              ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters(),
              workspaceId)
          .build();
  @Mock private MsiManager mockMsiManager;
  @Mock private CoreV1Api mockCoreV1Api;
  private final String oidcIssuer = UUID.randomUUID().toString();
  private final String uamiClientId = UUID.randomUUID().toString();
  @Mock private Identities mockIdentities;
  @Mock private MsiManager mockManager;
  @Mock private ManagedServiceIdentityClient mockServiceClient;
  @Mock private FederatedIdentityCredentialsClient mockFederatedIdentityCredentials;
  @Captor private ArgumentCaptor<FederatedIdentityCredentialInner> fedIdInnerCaptor;
  @Captor private ArgumentCaptor<V1ServiceAccount> serviceAccountCaptor;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;

  @Test
  void testSuccess() throws ApiException {
    setupMocks();

    when(mockCoreV1Api.createNamespacedServiceAccount(
            eq(k8sNamespace), serviceAccountCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(null);

    var step =
        new CreateFederatedIdentityStep(
            k8sNamespace,
            mockAzureConfig,
            mockCrlService,
            mockKubernetesClientProvider,
            mockLandingZoneApiDispatch,
            mockSamService,
            mockWorkspaceService,
            workspaceId,
            identityResource.getManagedIdentityName());
    var result =
        step.createFederatedIdentityAndK8sServiceAccount(
            identityResource.getManagedIdentityName(),
            mockAzureCloudContext,
            mockMsiManager,
            mockCoreV1Api,
            oidcIssuer,
            uamiClientId);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));

    assertThat(fedIdInnerCaptor.getValue().issuer(), equalTo(oidcIssuer));
    assertThat(
        fedIdInnerCaptor.getValue().subject(),
        equalTo(
            String.format(
                "system:serviceaccount:%s:%s",
                k8sNamespace, identityResource.getManagedIdentityName())));
    assertThat(
        fedIdInnerCaptor.getValue().audiences(), equalTo(List.of("api://AzureADTokenExchange")));

    assertThat(
        serviceAccountCaptor.getValue().getMetadata().getName(),
        equalTo(identityResource.getManagedIdentityName()));
    assertThat(serviceAccountCaptor.getValue().getMetadata().getNamespace(), equalTo(k8sNamespace));
    assertThat(
        serviceAccountCaptor.getValue().getMetadata().getAnnotations(),
        equalTo(Map.of("azure.workload.identity/client-id", uamiClientId)));
  }

  @Test
  void testKSAExists() throws ApiException {
    StepResult result = testError(HttpStatus.CONFLICT);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testRetryableError() throws ApiException {
    StepResult result = testError(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testFatalError() throws ApiException {
    StepResult result = testError(HttpStatus.BAD_REQUEST);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testAlreadyExists() throws InterruptedException {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(FEDERATED_IDENTITY_EXISTS, Boolean.class)).thenReturn(true);

    var step =
        new CreateFederatedIdentityStep(
            k8sNamespace,
            mockAzureConfig,
            mockCrlService,
            mockKubernetesClientProvider,
            mockLandingZoneApiDispatch,
            mockSamService,
            mockWorkspaceService,
            workspaceId,
            "ksaName");
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
  }

  private StepResult testError(HttpStatus httpStatus) throws ApiException {
    setupMocks();

    when(mockCoreV1Api.createNamespacedServiceAccount(
            eq(k8sNamespace), serviceAccountCaptor.capture(), any(), any(), any(), any()))
        .thenThrow(new ApiException(httpStatus.value(), httpStatus.name()));

    var step =
        new CreateFederatedIdentityStep(
            k8sNamespace,
            mockAzureConfig,
            mockCrlService,
            mockKubernetesClientProvider,
            mockLandingZoneApiDispatch,
            mockSamService,
            mockWorkspaceService,
            workspaceId,
            "ksaName");
    return step.createFederatedIdentityAndK8sServiceAccount(
        identityResource.getManagedIdentityName(),
        mockAzureCloudContext,
        mockMsiManager,
        mockCoreV1Api,
        oidcIssuer,
        uamiClientId);
  }

  private void setupMocks() {
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.manager()).thenReturn(mockManager);
    when(mockManager.serviceClient()).thenReturn(mockServiceClient);
    when(mockServiceClient.getFederatedIdentityCredentials())
        .thenReturn(mockFederatedIdentityCredentials);
    when(mockFederatedIdentityCredentials.createOrUpdate(
            eq(mockAzureCloudContext.getAzureResourceGroupId()),
            eq(identityResource.getManagedIdentityName()),
            eq(k8sNamespace),
            fedIdInnerCaptor.capture()))
        .thenReturn(null);
  }
}
