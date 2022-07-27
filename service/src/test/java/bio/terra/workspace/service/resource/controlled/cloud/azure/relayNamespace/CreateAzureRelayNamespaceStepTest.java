package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.azure.resourcemanager.relay.data.CreateRelayRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.Namespaces;
import com.azure.resourcemanager.relay.models.RelayNamespace;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class CreateAzureRelayNamespaceStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext dummyAzureCloudContext;
  @Mock private RelayManager mockRelayManager;
  @Mock private Namespaces mockNamespaces;
  @Mock private RelayNamespace.DefinitionStages.Blank mockStage1;
  @Mock private RelayNamespace.DefinitionStages.WithResourceGroup mockStage2;
  @Mock private RelayNamespace.DefinitionStages.WithCreate mockStage3;
  @Mock private RelayNamespace mockRelayNamespace;
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
    when(mockRelayManager.namespaces()).thenReturn(mockNamespaces);
    when(mockNamespaces.define(anyString())).thenReturn(mockStage1);
    when(mockStage1.withRegion(anyString())).thenReturn(mockStage2);

    when(mockStage2.withExistingResourceGroup(anyString())).thenReturn(mockStage3);
    when(mockStage3.create(any())).thenReturn(mockRelayNamespace);

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(dummyAzureCloudContext);

    // Deletion mocks
    doNothing().when(mockNamespaces).deleteByResourceGroup(anyString(), anyString());

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));
  }

  @Test
  void createRelayNamespace() throws InterruptedException {
    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    CreateAzureRelayNamespaceStep createAzureIpStep =
        new CreateAzureRelayNamespaceStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayNamespace(
                creationParameters.getNamespaceName(), creationParameters.getRegion()));

    final StepResult stepResult = createAzureIpStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockStage3).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateRelayRequestData> requestDataOpt =
        context.getValues().values().stream()
            .filter(CreateRelayRequestData.class::isInstance)
            .map(CreateRelayRequestData.class::cast)
            .findFirst();

    CreateRelayRequestData expected =
        CreateRelayRequestData.builder()
            .setName(creationParameters.getNamespaceName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setTenantId(dummyAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(dummyAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(dummyAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createRelayNamespace_alreadyExists() throws InterruptedException {
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    CreateAzureRelayNamespaceStep createStep =
        new CreateAzureRelayNamespaceStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayNamespace(
                creationParameters.getName(), creationParameters.getRegion()));

    // Stub creation to throw Conflict exception.
    when(mockStage3.create(any(Context.class))).thenThrow(mockException);

    final StepResult stepResult = createStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteRelayNamespace() throws InterruptedException {
    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    CreateAzureRelayNamespaceStep createStep =
        new CreateAzureRelayNamespaceStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureRelayNamespace(
                creationParameters.getNamespaceName(), creationParameters.getRegion()));

    final StepResult stepResult = createStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockNamespaces)
        .deleteByResourceGroup(
            dummyAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getNamespaceName());
  }
}
