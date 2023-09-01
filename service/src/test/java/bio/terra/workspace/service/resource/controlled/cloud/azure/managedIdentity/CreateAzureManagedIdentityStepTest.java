package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

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
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity.DefinitionStages.Blank;
import com.azure.resourcemanager.msi.models.Identity.DefinitionStages.WithCreate;
import com.azure.resourcemanager.msi.models.Identity.DefinitionStages.WithGroup;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class CreateAzureManagedIdentityStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private Blank mockIdentitiesStage1;
  @Mock private WithGroup mockIdentitiesStage2;
  @Mock private WithCreate mockIdentitiesStage3;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private HttpResponse mockHttpResponse;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;

  @Test
  void testAlreadyExists() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.CONFLICT);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testRetryableError() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testFatalError() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.BAD_REQUEST);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var withCreate = mockMsiCreate(workspaceId, identityResource, mockCrlService);
    when(withCreate.create(any())).thenReturn(null);

    var stepResult =
        new CreateAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource)
            .doStep(createMockFlightContext());
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testUndoNotExists() throws InterruptedException {
    StepResult stepResult = testUndoError(HttpStatus.NOT_FOUND);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testUndoSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    doNothing()
        .when(mockIdentities)
        .deleteByResourceGroup(any(), eq(identityResource.getManagedIdentityName()));

    var stepResult =
        new CreateAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource)
            .undoStep(createMockFlightContextForUndo());
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testUndoRetry() throws InterruptedException {
    assertThat(
        testUndoError(HttpStatus.INTERNAL_SERVER_ERROR).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testUndoFatal() throws InterruptedException {
    assertThat(
        testUndoError(HttpStatus.BAD_REQUEST).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private StepResult testUndoError(HttpStatus httpStatus) throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    doThrow(new ManagementException(httpStatus.name(), mockHttpResponse))
        .when(mockIdentities)
        .deleteByResourceGroup(any(), eq(identityResource.getManagedIdentityName()));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    return new CreateAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource)
        .undoStep(createMockFlightContextForUndo());
  }

  private StepResult testWithError(HttpStatus httpStatus) throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var withCreate = mockMsiCreate(workspaceId, identityResource, mockCrlService);
    when(withCreate.create(any()))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    return new CreateAzureManagedIdentityStep(mockAzureConfig, mockCrlService, identityResource)
        .doStep(createMockFlightContext());
  }

  private WithCreate mockMsiCreate(
      UUID workspaceId,
      ControlledAzureManagedIdentityResource identityResource,
      CrlService mockCrlService) {
    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.define(identityResource.getManagedIdentityName()))
        .thenReturn(mockIdentitiesStage1);
    when(mockIdentitiesStage1.withRegion(identityResource.getRegion()))
        .thenReturn(mockIdentitiesStage2);
    when(mockIdentitiesStage2.withExistingResourceGroup(any(String.class)))
        .thenReturn(mockIdentitiesStage3);
    when(mockIdentitiesStage3.withTag("workspaceId", workspaceId.toString()))
        .thenReturn(mockIdentitiesStage3);
    when(mockIdentitiesStage3.withTag("resourceId", identityResource.getResourceId().toString()))
        .thenReturn(mockIdentitiesStage3);

    return mockIdentitiesStage3;
  }

  private FlightContext createMockFlightContext() {
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(UUID.randomUUID().toString());
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(UUID.randomUUID().toString());

    return createMockFlightContextForUndo();
  }

  private FlightContext createMockFlightContextForUndo() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(UUID.randomUUID().toString());

    return mockFlightContext;
  }
}
