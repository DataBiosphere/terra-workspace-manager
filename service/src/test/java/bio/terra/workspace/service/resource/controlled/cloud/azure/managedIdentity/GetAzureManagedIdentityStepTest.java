package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class GetAzureManagedIdentityStepTest {
  private MockitoSession mockito;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private CrlService mockCrlService;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private HttpResponse mockHttpResponse;

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
  void testSuccess() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.NOT_FOUND);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testFatal() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.BAD_REQUEST);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testRetry() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testAlreadyExists() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters = ControlledResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    createMockFlightContext();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenReturn(null);

    GetAzureManagedIdentityStep step =
        new GetAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource);
    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private StepResult testWithError(HttpStatus httpStatus) throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters = ControlledResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    createMockFlightContext();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    GetAzureManagedIdentityStep step =
        new GetAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource);
    return step.doStep(mockFlightContext);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(UUID.randomUUID().toString());

    return mockFlightContext;
  }
}
