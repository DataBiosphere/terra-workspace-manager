package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@Tag("azure-unit")
public class CreateDatabaseUserStepTest {
  private MockitoSession mockito;
  @Mock private AzureDatabaseUtilsRunner mockAzureDatabaseUtilsRunner;
  @Mock private ResourceDao mockResourceDao;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;

  @BeforeEach
  public void setup() {
    // initialize session to start mocking
    mockito =
        Mockito.mockitoSession().initMocks(this).strictness(Strictness.STRICT_STUBS).startMocking();
  }

  @AfterEach
  public void tearDown() {
    mockito.finishMocking();
  }

  @Test
  void testSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var userOid = UUID.randomUUID().toString();
    var owner = UUID.randomUUID();
    var dbCreationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(owner, null);

    var dbResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                dbCreationParameters, workspaceId)
            .build();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(dbResource.getResourceId()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockResourceDao.getResource(workspaceId, dbResource.getResourceId()))
        .thenReturn(dbResource);
    when(mockWorkingMap.get(GetManagedIdentityStep.MANAGED_IDENTITY_PRINCIPAL_ID, String.class))
        .thenReturn(userOid);

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    assertThat(step.doStep(createMockFlightContext()), equalTo(StepResult.getStepResultSuccess()));

    verify(mockAzureDatabaseUtilsRunner)
        .createNamespaceRole(
            mockAzureCloudContext,
            workspaceId,
            "create-namespace-role-" + resource.getKubernetesServiceAccount(),
            resource.getKubernetesServiceAccount(),
            userOid,
            Set.of(dbResource.getDatabaseName()));
  }

  @Test
  void testNotADatabase() throws InterruptedException {
    var workspaceId = UUID.randomUUID();

    var notADbResource = ControlledAzureResourceFixtures.makeAzureVm(workspaceId);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID(), List.of(notADbResource.getResourceId()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockResourceDao.getResource(workspaceId, notADbResource.getResourceId()))
        .thenReturn(notADbResource);

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testDatabaseDoesNotExist() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var nonExistentId = UUID.randomUUID();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID(), List.of(nonExistentId));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    when(mockResourceDao.getResource(workspaceId, nonExistentId))
        .thenThrow(new ResourceNotFoundException("not found"));

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
