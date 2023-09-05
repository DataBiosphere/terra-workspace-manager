package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProvider;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class CreateKubernetesNamespaceStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private CoreV1Api mockCoreV1Api;

  @Test
  void testDoStep() throws Exception {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(mockCoreV1Api);

    var result =
        new CreateKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockCoreV1Api)
        .createNamespace(
            new V1Namespace().metadata(new V1ObjectMeta().name(resource.getKubernetesNamespace())),
            null,
            null,
            null,
            null);
  }

  @Test
  void testDoStepConflict() throws Exception {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(mockCoreV1Api);
    when(mockCoreV1Api.createNamespace(
            new V1Namespace().metadata(new V1ObjectMeta().name(resource.getKubernetesNamespace())),
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException(HttpStatus.CONFLICT.value(), "message"));
    when(mockKubernetesClientProvider.stepResultFromException(any(), any())).thenCallRealMethod();

    var result =
        new CreateKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .doStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void testUndoStep() throws Exception {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(mockCoreV1Api);

    var result =
        new CreateKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .undoStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockCoreV1Api)
        .deleteNamespace(resource.getKubernetesNamespace(), null, null, null, null, null, null);
  }

  @Test
  void testUndoStepNotFound() throws Exception {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockKubernetesClientProvider.createCoreApiClient(mockAzureCloudContext, workspaceId))
        .thenReturn(mockCoreV1Api);
    when(mockCoreV1Api.deleteNamespace(
            resource.getKubernetesNamespace(), null, null, null, null, null, null))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "message"));
    when(mockKubernetesClientProvider.stepResultFromException(any(), any()))
        .thenCallRealMethod()
        .thenReturn(StepResult.getStepResultSuccess());

    var result =
        new CreateKubernetesNamespaceStep(workspaceId, mockKubernetesClientProvider, resource)
            .undoStep(createMockFlightContext());

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
