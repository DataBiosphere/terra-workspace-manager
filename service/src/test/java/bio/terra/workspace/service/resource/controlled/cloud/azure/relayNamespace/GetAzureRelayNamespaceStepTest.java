package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.Namespaces;
import com.azure.resourcemanager.relay.models.RelayNamespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class GetAzureRelayNamespaceStepTest extends BaseAzureConnectedTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private RelayManager mockRelayManager;
  @Mock private Namespaces mockNamespaces;
  @Mock private RelayNamespace.DefinitionStages.Blank mockStage1;
  @Mock private RelayNamespace.DefinitionStages.WithResourceGroup mockStage2;
  @Mock private RelayNamespace.DefinitionStages.WithCreate mockStage3;
  @Mock private RelayNamespace mockRelayNamespace;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  @BeforeEach
  public void setup() {
    // PublicIpAddresses mocks
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getRelayManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockRelayManager);
    when(mockRelayManager.namespaces()).thenReturn(mockNamespaces);
    when(mockNamespaces.define(anyString())).thenReturn(mockStage1);
    when(mockStage1.withRegion(anyString())).thenReturn(mockStage2);

    when(mockStage2.withExistingResourceGroup(anyString())).thenReturn(mockStage3);
    when(mockStage3.create(any())).thenReturn(mockRelayNamespace);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  public void getRelayNamespace_doesNotExist() throws InterruptedException {
    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    GetAzureRelayNamespaceStep getStep =
        new GetAzureRelayNamespaceStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayNamespace(
                creationParameters.getNamespaceName(), creationParameters.getRegion()));

    when(mockNamespaces.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getNamespaceName()))
        .thenThrow(mockException);

    final StepResult stepResult = getStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getRelayNamespace_alreadyExists() throws InterruptedException {
    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    GetAzureRelayNamespaceStep getStep =
        new GetAzureRelayNamespaceStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayNamespace(
                creationParameters.getNamespaceName(), creationParameters.getRegion()));

    when(mockNamespaces.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getNamespaceName()))
        .thenReturn(mockRelayNamespace);

    final StepResult stepResult = getStep.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
