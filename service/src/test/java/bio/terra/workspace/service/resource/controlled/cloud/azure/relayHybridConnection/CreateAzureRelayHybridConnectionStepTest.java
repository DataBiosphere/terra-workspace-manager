package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.cloudres.azure.resourcemanager.relay.data.CreateRelayHybridConnectionRequestData;
import bio.terra.cloudres.azure.resourcemanager.relay.data.CreateRelayRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureRelayHybridConnectionCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.HybridConnection;
import com.azure.resourcemanager.relay.models.HybridConnections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AZURE_RELAY_NAMESPACE_NAME_PREFIX;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.uniqueAzureName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("azure")
public class CreateAzureRelayHybridConnectionStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext dummyAzureCloudContext;
  @Mock private RelayManager mockRelayManager;
  @Mock private HybridConnections mockHybridConnections;
  @Mock private HybridConnection.DefinitionStages.Blank mockStage1;
  @Mock private HybridConnection.DefinitionStages.WithCreate mockStage2;
  @Mock private HybridConnection.DefinitionStages.WithCreate mockStage3;
  @Mock private HybridConnection mockRelayHybridConnection;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    when(dummyAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(dummyAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(dummyAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getRelayManager(dummyAzureCloudContext, mockAzureConfig))
        .thenReturn(mockRelayManager);

    when(dummyAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getRelayManager(dummyAzureCloudContext, mockAzureConfig))
        .thenReturn(mockRelayManager);
    when(mockRelayManager.hybridConnections()).thenReturn(mockHybridConnections);
    when(mockHybridConnections.define(anyString())).thenReturn(mockStage1);
    when(mockStage1.withExistingNamespace(anyString(), anyString())).thenReturn(mockStage2);

    when(mockStage2.withRequiresClientAuthorization(anyBoolean())).thenReturn(mockStage3);
    when(mockStage3.create(any())).thenReturn(mockRelayHybridConnection);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(dummyAzureCloudContext);

    // Deletion mocks
    doNothing().when(mockHybridConnections).deleteById(anyString());

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(dummyAzureCloudContext);
  }

  @Test
  void createRelayHybridConnection() throws InterruptedException {
    final ApiAzureRelayHybridConnectionCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayHybridConnectionCreationParameters(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX));

    CreateAzureRelayHybridConnectionStep createAzureIpStep =
        new CreateAzureRelayHybridConnectionStep(
            mockAzureConfig,
            mockCrlService,
                ControlledResourceFixtures.getAzureRelayHybridConnection(
                        creationParameters.getNamespaceName(), creationParameters.getHybridConnectionName(), creationParameters.isRequiresClientAuthorization()));

    final StepResult stepResult = createAzureIpStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockStage3).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateRelayHybridConnectionRequestData> requestDataOpt =
        context.getValues().values().stream()
            .filter(CreateRelayHybridConnectionRequestData.class::isInstance)
            .map(CreateRelayHybridConnectionRequestData.class::cast)
            .findFirst();

    CreateRelayHybridConnectionRequestData expected =
          CreateRelayHybridConnectionRequestData.builder()
            .setName(creationParameters.getHybridConnectionName())
            .setRegion(Region.US_CENTRAL) //TODO: bump CRL version with https://github.com/DataBiosphere/terra-cloud-resource-lib/pull/117
            .setTenantId(dummyAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(dummyAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(dummyAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createRelayHybridConnection_alreadyExists() throws InterruptedException {
    final ApiAzureRelayHybridConnectionCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayHybridConnectionCreationParameters(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX));

    CreateAzureRelayHybridConnectionStep createStep =
        new CreateAzureRelayHybridConnectionStep(
            mockAzureConfig,
            mockCrlService,
                ControlledResourceFixtures.getAzureRelayHybridConnection(
                        creationParameters.getNamespaceName(), creationParameters.getHybridConnectionName(), creationParameters.isRequiresClientAuthorization()));

    // Stub creation to throw Conflict exception.
    when(mockStage3.create(any(Context.class))).thenThrow(mockException);

    final StepResult stepResult = createStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteRelayHybridConnection() throws InterruptedException {
    final ApiAzureRelayHybridConnectionCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayHybridConnectionCreationParameters(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX));

    CreateAzureRelayHybridConnectionStep createStep =
        new CreateAzureRelayHybridConnectionStep(
            mockAzureConfig,
            mockCrlService,
                ControlledResourceFixtures.getAzureRelayHybridConnection(
                        creationParameters.getNamespaceName(), creationParameters.getHybridConnectionName(), creationParameters.isRequiresClientAuthorization()));

    final StepResult stepResult = createStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockHybridConnections)
        .delete(anyString(), anyString(), anyString());
  }
}
