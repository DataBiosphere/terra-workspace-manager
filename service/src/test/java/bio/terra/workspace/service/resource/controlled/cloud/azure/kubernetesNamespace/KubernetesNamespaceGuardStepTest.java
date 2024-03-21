package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class KubernetesNamespaceGuardStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private CoreV1Api mockCoreV1Api;

  @Test
  void testDoStepSuccess() throws InterruptedException, ApiException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(Optional.of(mockCoreV1Api));
    when(mockCoreV1Api.readNamespace(resource.getKubernetesNamespace(), null))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "not found"));
    when(mockKubernetesClientProvider.stepResultFromException(any(), any())).thenCallRealMethod();

    var result =
        new KubernetesNamespaceGuardStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void testDoStepExists() throws InterruptedException, ApiException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(Optional.of(mockCoreV1Api));
    when(mockCoreV1Api.readNamespace(resource.getKubernetesNamespace(), null))
        .thenReturn(new V1Namespace());

    var result =
        new KubernetesNamespaceGuardStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
