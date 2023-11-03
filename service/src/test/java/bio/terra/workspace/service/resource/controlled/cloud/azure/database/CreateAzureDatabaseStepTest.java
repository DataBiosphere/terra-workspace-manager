package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Databases;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class CreateAzureDatabaseStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private CrlService mockCrlService;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private FlightContext mockFlightContext;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private ApiAzureLandingZoneDeployedResource mockDatabase;
  @Mock private PostgreSqlManager mockPostgreSqlManager;
  @Mock private Databases mockDatabases;
  @Mock private HttpResponse mockHttpResponse;
  @Mock private AzureDatabaseUtilsRunner mockAzureDatabaseUtilsRunner;

  private final UUID workspaceId = UUID.randomUUID();
  private final String uamiName = UUID.randomUUID().toString();
  private final String uamiPrincipalId = UUID.randomUUID().toString();
  private final ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, false);
  private final ControlledAzureDatabaseResource databaseResource =
      ControlledAzureResourceFixtures.makePrivateControlledAzureDatabaseResourceBuilder(
              creationParameters, workspaceId, null)
          .build();

  @Test
  void testSuccess() throws InterruptedException {
    var step = setupStepTest();
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
    verify(mockAzureDatabaseUtilsRunner)
        .createDatabaseWithDbRole(
            mockAzureCloudContext,
            workspaceId,
            "create-" + databaseResource.getResourceId(),
            databaseResource.getDatabaseName());
  }

  @Test
  void testUndo() throws InterruptedException {
    final String databaseServerName = UUID.randomUUID().toString();
    CreateAzureDatabaseStep step = setupUndoTest(databaseServerName, workspaceId, databaseResource);

    doNothing()
        .when(mockDatabases)
        .delete(any(), eq(databaseServerName), eq(databaseResource.getDatabaseName()));

    var result = step.undoStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  void testUndoDatabaseDoesNotExist() throws InterruptedException {
    final String databaseServerName = UUID.randomUUID().toString();

    CreateAzureDatabaseStep step = setupUndoTest(databaseServerName, workspaceId, databaseResource);

    HttpStatus httpStatus = HttpStatus.NOT_FOUND;
    doThrow(new ManagementException(httpStatus.name(), mockHttpResponse))
        .when(mockDatabases)
        .delete(any(), eq(databaseServerName), eq(databaseResource.getDatabaseName()));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    var result = step.undoStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @NotNull
  private CreateAzureDatabaseStep setupUndoTest(
      String databaseServerName,
      UUID workspaceId,
      ControlledAzureDatabaseResource databaseResource) {
    createMockFlightContext();

    when(mockCrlService.getPostgreSqlManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockPostgreSqlManager);
    when(mockPostgreSqlManager.databases()).thenReturn(mockDatabases);

    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockDatabase.getResourceId()).thenReturn(databaseServerName);

    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());

    return new CreateAzureDatabaseStep(
        mockAzureConfig,
        mockCrlService,
        databaseResource,
        mockLandingZoneApiDispatch,
        mockSamService,
        mockWorkspaceService,
        workspaceId,
        mockAzureDatabaseUtilsRunner);
  }

  @NotNull
  private CreateAzureDatabaseStep setupStepTest() {
    createMockFlightContext();

    return new CreateAzureDatabaseStep(
        mockAzureConfig,
        mockCrlService,
        databaseResource,
        mockLandingZoneApiDispatch,
        mockSamService,
        mockWorkspaceService,
        workspaceId,
        mockAzureDatabaseUtilsRunner);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
