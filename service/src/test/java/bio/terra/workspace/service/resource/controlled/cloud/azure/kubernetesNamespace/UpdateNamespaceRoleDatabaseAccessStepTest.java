package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.Direction;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class UpdateNamespaceRoleDatabaseAccessStepTest extends BaseMockitoStrictStubbingTest {

  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;
  @Mock private AzureDatabaseUtilsRunner mockAzureDatabaseUtilsRunner;
  @Mock private ResourceDao mockResourceDao;

  @Test
  void testDoRevoke() throws InterruptedException {
    runRevokeTest(
        step -> step.doStep(mockFlightContext),
        Direction.DO,
        UpdateNamespaceRoleDatabaseAccessStepMode.REVOKE);
  }

  @Test
  void testUndoRevoke() throws InterruptedException {
    // undoing a revoke is a restore
    runRestoreTest(
        step -> step.undoStep(mockFlightContext),
        Direction.UNDO,
        UpdateNamespaceRoleDatabaseAccessStepMode.REVOKE);
  }

  @Test
  void testDoRestore() throws InterruptedException {
    runRestoreTest(
        step -> step.doStep(mockFlightContext),
        Direction.DO,
        UpdateNamespaceRoleDatabaseAccessStepMode.RESTORE);
  }

  @Test
  void testUndoRestore() throws InterruptedException {
    // undoing a restore is a revoke
    runRevokeTest(
        step -> step.undoStep(mockFlightContext),
        Direction.UNDO,
        UpdateNamespaceRoleDatabaseAccessStepMode.RESTORE);
  }

  @Test
  void testDoRestoreAbandoned() throws InterruptedException {
    var resource = createK8sNamespaceResource(PrivateResourceState.ABANDONED);
    when(mockResourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()))
        .thenReturn(resource);
    var step =
        new UpdateNamespaceRoleDatabaseAccessStep(
            resource.getWorkspaceId(),
            mockAzureDatabaseUtilsRunner,
            resource,
            mockResourceDao,
            UpdateNamespaceRoleDatabaseAccessStepMode.RESTORE);

    var results = step.doStep(mockFlightContext);

    assertThat(results.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  void runRevokeTest(
      RunStep runStep, Direction direction, UpdateNamespaceRoleDatabaseAccessStepMode stepMode)
      throws InterruptedException {
    setupMockFlightContext();
    var resource = createK8sNamespaceResource(PrivateResourceState.ACTIVE);
    when(mockFlightContext.getDirection()).thenReturn(direction);
    var step =
        new UpdateNamespaceRoleDatabaseAccessStep(
            resource.getWorkspaceId(),
            mockAzureDatabaseUtilsRunner,
            resource,
            mockResourceDao,
            stepMode);

    var results = runStep.run(step);

    assertThat(results.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    assertThat(step.getPodName(mockFlightContext).length(), lessThanOrEqualTo(63));
    verify(mockAzureDatabaseUtilsRunner)
        .revokeNamespaceRoleAccess(
            mockAzureCloudContext,
            resource.getWorkspaceId(),
            step.getPodName(mockFlightContext),
            resource.getKubernetesServiceAccount());
  }

  void runRestoreTest(
      RunStep runStep, Direction direction, UpdateNamespaceRoleDatabaseAccessStepMode stepMode)
      throws InterruptedException {
    setupMockFlightContext();
    var resource = createK8sNamespaceResource(PrivateResourceState.ACTIVE);
    when(mockResourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()))
        .thenReturn(resource);
    when(mockFlightContext.getDirection()).thenReturn(direction);
    var step =
        new UpdateNamespaceRoleDatabaseAccessStep(
            resource.getWorkspaceId(),
            mockAzureDatabaseUtilsRunner,
            resource,
            mockResourceDao,
            stepMode);

    var results = runStep.run(step);

    assertThat(results.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    assertThat(step.getPodName(mockFlightContext).length(), lessThanOrEqualTo(63));
    verify(mockAzureDatabaseUtilsRunner)
        .restoreNamespaceRoleAccess(
            mockAzureCloudContext,
            resource.getWorkspaceId(),
            step.getPodName(mockFlightContext),
            resource.getKubernetesServiceAccount());
  }

  private ControlledAzureKubernetesNamespaceResource createK8sNamespaceResource(
      PrivateResourceState privateResourceState) {
    return ControlledAzureResourceFixtures
        .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
            ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
                null, List.of("db")),
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            privateResourceState)
        .build();
  }

  private void setupMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }
}

@FunctionalInterface
interface RunStep {
  StepResult run(UpdateNamespaceRoleDatabaseAccessStep step) throws InterruptedException;
}
