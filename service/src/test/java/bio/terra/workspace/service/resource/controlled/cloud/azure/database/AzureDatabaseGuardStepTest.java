package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Databases;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class AzureDatabaseGuardStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private HttpResponse mockHttpResponse;
  @Mock private PostgreSqlManager mockPostgreSqlManager;
  @Mock private Databases mockDatabases;
  @Mock private SamService mockSamService;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private ApiAzureLandingZoneDeployedResource mockDatabase;
  @Mock private WorkspaceService mockWorkspaceService;

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
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID().toString(), false);
    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId, CloningInstructions.COPY_NOTHING)
            .build();

    createMockFlightContext();

    setupMocksUntilGetCall();
    when(mockDatabases.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            mockDatabase.getResourceId(),
            databaseResource.getDatabaseName()))
        .thenReturn(null);

    var step =
        new AzureDatabaseGuardStep(
            mockAzureConfig,
            mockCrlService,
            databaseResource,
            mockSamService,
            mockLandingZoneApiDispatch,
            mockWorkspaceService,
            workspaceId);
    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private StepResult testWithError(HttpStatus httpStatus) throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID().toString(), false);
    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId, CloningInstructions.COPY_NOTHING)
            .build();

    createMockFlightContext();

    setupMocksUntilGetCall();
    when(mockDatabases.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            mockDatabase.getResourceId(),
            databaseResource.getDatabaseName()))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    var step =
        new AzureDatabaseGuardStep(
            mockAzureConfig,
            mockCrlService,
            databaseResource,
            mockSamService,
            mockLandingZoneApiDispatch,
            mockWorkspaceService,
            workspaceId);
    return step.doStep(mockFlightContext);
  }

  private void setupMocksUntilGetCall() {
    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());
    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenReturn(UUID.randomUUID());
    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockDatabase.getResourceId()).thenReturn(UUID.randomUUID().toString());
    when(mockCrlService.getPostgreSqlManager(any(), any())).thenReturn(mockPostgreSqlManager);
    when(mockPostgreSqlManager.databases()).thenReturn(mockDatabases);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(UUID.randomUUID().toString());

    return mockFlightContext;
  }
}
