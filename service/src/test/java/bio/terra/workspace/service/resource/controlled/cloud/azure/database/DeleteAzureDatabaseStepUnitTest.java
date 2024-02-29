package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Databases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

@Tag("unit")
public class DeleteAzureDatabaseStepUnitTest extends BaseMockitoStrictStubbingTest {

  @Mock
  AzureConfiguration azureConfig;
  @Mock
  CrlService crlService;
  @Mock
  SamService samService;
  @Mock
  WorkspaceService workspaceService;
  @Mock
  LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock
  ControlledAzureDatabaseResource resource;
  @Mock
  AzureCloudContext azureCloudContext;
  @Mock
  PostgreSqlManager postgresManager;
  @Mock
  Databases databases;

  @Mock
  FlightMap workingMap;
  @Mock
  FlightContext context;

  @BeforeEach
  void localSetup() {
    // since strict stubbing is enabled, we don't want to do much here,
    // but it's probably safe to say all the tests will need the flight context to return the working map
    when(context.getWorkingMap()).thenReturn(workingMap);
  }


  @Test
  void deletingDatabaseHappyPath() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    when(workingMap.get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    var landingZoneId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var deployedDatabase = mock(ApiAzureLandingZoneDeployedResource.class);
    var deployedDatabaseName = "deployed-database-name";
    when(landingZoneApiDispatch.getSharedDatabase(token, landingZoneId)).thenReturn(Optional.of(deployedDatabase));

    var resourceDBName = "resource-name";
    when(resource.getDatabaseName()).thenReturn(resourceDBName);

    when(deployedDatabase.getResourceId()).thenReturn("id-prefix/%s".formatted(deployedDatabaseName));

    when(crlService.getPostgreSqlManager(azureCloudContext, azureConfig)).thenReturn(postgresManager);
    when(postgresManager.databases()).thenReturn(databases);
    doNothing().when(databases).delete(resourceGroupId, deployedDatabaseName, resourceDBName);

    var step = new DeleteAzureDatabaseStep(
        azureConfig,
        crlService,
        resource,
        landingZoneApiDispatch,
        samService,
        workspaceService,
        workspaceId
    );
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }


}
