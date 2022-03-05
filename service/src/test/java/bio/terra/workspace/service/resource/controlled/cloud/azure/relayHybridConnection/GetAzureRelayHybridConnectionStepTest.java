package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureRelayHybridConnectionCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.HybridConnection;
import com.azure.resourcemanager.relay.models.HybridConnections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AZURE_RELAY_NAMESPACE_NAME_PREFIX;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.uniqueAzureName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ActiveProfiles("azure")
public class GetAzureRelayHybridConnectionStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private RelayManager mockRelayManager;
  @Mock private HybridConnections mockHybridConnections;
  @Mock private HybridConnection mockHybridConnection;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  @BeforeEach
  public void setup() {
    // PublicIpAddresses mocks
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getRelayManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockRelayManager);
    when(mockRelayManager.hybridConnections()).thenReturn(mockHybridConnections);
    when(mockHybridConnections.getById(any())).thenReturn(mockHybridConnection);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  public void getHybridConnection_doesNotExist() throws InterruptedException {
    final ApiAzureRelayHybridConnectionCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayHybridConnectionCreationParameters(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX));

    GetAzureRelayHybridConnectionStep getStep =
        new GetAzureRelayHybridConnectionStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayHybridConnection(
                creationParameters.getNamespaceName(), creationParameters.getHybridConnectionName(), creationParameters.isRequiresClientAuthorization()));

    when(mockHybridConnections.getById(anyString()))
        .thenThrow(mockException);

    final StepResult stepResult = getStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getHybridConnection_alreadyExists() throws InterruptedException {
    final ApiAzureRelayHybridConnectionCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayHybridConnectionCreationParameters(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX));

    GetAzureRelayHybridConnectionStep getStep =
        new GetAzureRelayHybridConnectionStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayHybridConnection(
                creationParameters.getNamespaceName(), creationParameters.getHybridConnectionName(), creationParameters.isRequiresClientAuthorization()));

    when(mockHybridConnections.getById(anyString()))
        .thenReturn(mockHybridConnection);

    final StepResult stepResult = getStep.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
