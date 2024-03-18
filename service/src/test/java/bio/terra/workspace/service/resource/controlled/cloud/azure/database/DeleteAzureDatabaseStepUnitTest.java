package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Databases;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class DeleteAzureDatabaseStepUnitTest {

  @Mock AzureConfiguration azureConfig;
  @Mock CrlService crlService;
  @Mock SamService samService;
  @Mock WorkspaceService workspaceService;
  @Mock LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock ControlledAzureDatabaseResource resource;
  @Mock PostgreSqlManager postgresManager;
  @Mock Databases databases;

  @Mock AzureCloudContext azureCloudContext;
  @Mock FlightMap workingMap;
  @Mock FlightContext context;

  @BeforeEach
  void setup() {
    when(context.getWorkingMap()).thenReturn(workingMap);
    when(postgresManager.databases()).thenReturn(databases);
    when(workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    when(crlService.getPostgreSqlManager(azureCloudContext, azureConfig))
        .thenReturn(postgresManager);
  }

  @Test
  void deletingDatabaseHappyPath() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var landingZoneId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var deployedDatabase = mock(ApiAzureLandingZoneDeployedResource.class);
    var deployedDatabaseName = "deployed-database-name";
    when(landingZoneApiDispatch.getSharedDatabase(token, landingZoneId))
        .thenReturn(Optional.of(deployedDatabase));

    var resourceDBName = "resource-name";
    when(resource.getDatabaseName()).thenReturn(resourceDBName);

    when(deployedDatabase.getResourceId())
        .thenReturn("id-prefix/%s".formatted(deployedDatabaseName));

    doNothing().when(databases).delete(resourceGroupId, deployedDatabaseName, resourceDBName);

    var step =
        new DeleteAzureDatabaseStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            workspaceService,
            workspaceId);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void noResourceFoundReturnsSuccess() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var landingZoneId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var deployedDatabase = mock(ApiAzureLandingZoneDeployedResource.class);
    var deployedDatabaseName = "deployed-database-name";
    when(landingZoneApiDispatch.getSharedDatabase(token, landingZoneId))
        .thenReturn(Optional.of(deployedDatabase));

    var resourceDBName = "resource-name";
    when(resource.getDatabaseName()).thenReturn(resourceDBName);

    when(deployedDatabase.getResourceId())
        .thenReturn("id-prefix/%s".formatted(deployedDatabaseName));

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(404);
    var error = new ManagementError("NotFound", AzureManagementExceptionUtils.RESOURCE_NOT_FOUND);
    var exception =
        new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);

    doThrow(exception)
        .when(databases)
        .delete(resourceGroupId, deployedDatabaseName, resourceDBName);

    var step =
        new DeleteAzureDatabaseStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            workspaceService,
            workspaceId);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void unknownExceptionRetries() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var landingZoneId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var deployedDatabase = mock(ApiAzureLandingZoneDeployedResource.class);
    var deployedDatabaseName = "deployed-database-name";
    when(landingZoneApiDispatch.getSharedDatabase(token, landingZoneId))
        .thenReturn(Optional.of(deployedDatabase));

    var resourceDBName = "resource-name";
    when(resource.getDatabaseName()).thenReturn(resourceDBName);

    when(deployedDatabase.getResourceId())
        .thenReturn("id-prefix/%s".formatted(deployedDatabaseName));

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(500);
    var error = new ManagementError("UnhandledException", "some message");
    var exception = new ManagementException("some message", response, error);

    doThrow(exception)
        .when(databases)
        .delete(resourceGroupId, deployedDatabaseName, resourceDBName);

    var step =
        new DeleteAzureDatabaseStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            workspaceService,
            workspaceId);
    assertThat(step.doStep(context).getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void resourceMovedSubscriptionsReturnsSuccess() throws Exception {
    var resourceGroupId = "test-resource-group-id";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(resourceGroupId);
    var landingZoneId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var deployedDatabase = mock(ApiAzureLandingZoneDeployedResource.class);
    var deployedDatabaseName = "deployed-database-name";
    when(landingZoneApiDispatch.getSharedDatabase(token, landingZoneId))
        .thenReturn(Optional.of(deployedDatabase));

    var resourceDBName = "resource-name";
    when(resource.getDatabaseName()).thenReturn(resourceDBName);

    when(deployedDatabase.getResourceId())
        .thenReturn("id-prefix/%s".formatted(deployedDatabaseName));

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(401);
    var error =
        new ManagementError("InvalidAuthenticationTokenTenant", "some message about wrong issuer");
    var exception = new ManagementException("some message about wrong issuer", response, error);

    doThrow(exception)
        .when(databases)
        .delete(resourceGroupId, deployedDatabaseName, resourceDBName);

    var step =
        new DeleteAzureDatabaseStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            workspaceService,
            workspaceId);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }
}
