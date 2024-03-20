package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("unit")
public class DeleteKubernetesNamespaceStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private CoreV1Api mockCoreV1Api;
  @Mock private CoreV1Api.APIreadNamespaceRequest mockReadNamespaceRequest;

  @Test
  void testDoStepSuccess() throws Exception {
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
    when(mockCoreV1Api.readNamespace(resource.getKubernetesNamespace()))
        .thenReturn(mockReadNamespaceRequest);
    when(mockReadNamespaceRequest.execute())
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "Not found"));

    var result =
        new DeleteKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void testNoClientApiClientAvailable() throws Exception {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(Optional.empty());

    var result =
        new DeleteKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
