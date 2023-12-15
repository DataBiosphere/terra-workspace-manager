package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureSasBundle;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

@Tag("azure-unit")
class DumpAzureDatabaseStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private FlightContext mockFlightContext;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightMap mockInputParameters;
  @Mock private AzureDatabaseUtilsRunner mockAzureDatabaseUtilsRunner;
  @Mock private ResourceDao mockResourceDao;
  @Mock private AzureStorageAccessService mockAzureStorageAccessService;

  private final String databaseServerName = UUID.randomUUID().toString();
  private final String databaseUserName = UUID.randomUUID().toString();
  private final ApiAzureLandingZoneDeployedResource mockDatabase =
      new ApiAzureLandingZoneDeployedResource().resourceId(databaseServerName);
  private final ApiAzureLandingZoneDeployedResource mockDatabaseIdentity =
      new ApiAzureLandingZoneDeployedResource().resourceId(databaseUserName);

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID mockDestinationWorkspaceId = UUID.randomUUID();
  private final AuthenticatedUserRequest mockUserRequest = new AuthenticatedUserRequest();
  private final AzureSasBundle mockSasBundle =
      new AzureSasBundle("sas-token-foo", "url?sas-token-foo", "sha");
  private final ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, false);
  private final ControlledAzureDatabaseResource databaseResource =
      ControlledAzureResourceFixtures.makePrivateControlledAzureDatabaseResourceBuilder(
              creationParameters, workspaceId, null)
          .build();
  private final String mockEncryptionKey = "mock-encryption-key-123";

  @Test
  void testSuccess() throws InterruptedException {
    var step = setupStepTest();
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    ArgumentMatcher<String> matchesDumpFile =
        (String blobFileName) ->
            blobFileName.matches(
                "%s_[0-9-_:]+\\.dump".formatted(databaseResource.getDatabaseName()));

    verify(mockFlightContext.getWorkingMap())
        .put(
            eq(ControlledResourceKeys.AZURE_STORAGE_CONTAINER),
            argThat((ControlledAzureStorageContainerResource resource) -> resource != null));
    verify(mockFlightContext.getWorkingMap())
        .put(eq(ControlledResourceKeys.CLONE_DB_DUMPFILE), argThat(matchesDumpFile));
    verify(mockAzureDatabaseUtilsRunner)
        .pgDumpDatabase(
            eq(mockAzureCloudContext),
            eq(databaseResource.getWorkspaceId()),
            eq("dump-db-%s".formatted(databaseResource.getResourceId())),
            eq(databaseResource.getDatabaseName()),
            eq(databaseServerName),
            eq(databaseUserName),
            argThat(matchesDumpFile),
            eq("sc-%s".formatted(mockDestinationWorkspaceId)),
            eq(mockSasBundle.sasUrl()),
            eq(mockEncryptionKey));
  }

  @NotNull
  private DumpAzureDatabaseStep setupStepTest() {
    createMockFlightContext();

    return new DumpAzureDatabaseStep(
        databaseResource,
        mockLandingZoneApiDispatch,
        mockSamService,
        mockWorkspaceService,
        mockAzureStorageAccessService,
        mockAzureDatabaseUtilsRunner);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockFlightContext.getInputParameters()).thenReturn(mockInputParameters);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockInputParameters.get(
            JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class))
        .thenReturn(mockUserRequest);
    when(mockInputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class))
        .thenReturn(mockDestinationWorkspaceId);
    when(mockWorkingMap.get(
            ControlledResourceKeys.AZURE_STORAGE_CONTAINER,
            ControlledAzureStorageContainerResource.class))
        .thenReturn(
            ControlledAzureStorageContainerResource.builder()
                .common(
                    ControlledResourceFixtures.makeDefaultControlledResourceFields(
                        mockDestinationWorkspaceId))
                .storageContainerName("sc-%s".formatted(mockDestinationWorkspaceId.toString()))
                .build());
    when(mockWorkingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMP_ENCRYPTION_KEY,
            String.class))
        .thenReturn(mockEncryptionKey);
    when(mockAzureStorageAccessService.createAzureStorageContainerSasToken(
            eq(mockDestinationWorkspaceId),
            any(ControlledAzureStorageContainerResource.class),
            eq(mockUserRequest),
            nullable(String.class),
            nullable(String.class),
            anyString()))
        .thenReturn(mockSasBundle);
    when(mockSamService.getWsmServiceAccountToken()).thenReturn(UUID.randomUUID().toString());
    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenReturn(UUID.randomUUID());
    when(mockLandingZoneApiDispatch.getSharedDatabase(any(), any()))
        .thenReturn(Optional.of(mockDatabase));
    when(mockLandingZoneApiDispatch.getSharedDatabaseAdminIdentity(any(), any()))
        .thenReturn(Optional.of(mockDatabaseIdentity));

    return mockFlightContext;
  }
}
