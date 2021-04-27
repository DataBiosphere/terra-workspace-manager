package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateServiceAccountStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.notebooks.v1.model.Instance;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public class ControlledResourceServiceTest extends BaseConnectedTest {
  /** The default roles to use when creating user private AI notebook instance resources */
  private static final List<ControlledResourceIamRole> DEFAULT_ROLES =
      // Need to be careful about what subclass of List gets put in a FlightMap.
      Stream.of(
              ControlledResourceIamRole.OWNER,
              ControlledResourceIamRole.WRITER,
              ControlledResourceIamRole.EDITOR)
          .collect(Collectors.toList());

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;

  private static Workspace reusableWorkspace;

  /**
   * Retrieve or create a workspace ready for controlled notebook instance resources.
   *
   * <p>Reusing a workspace saves time between tests.
   */
  private Workspace reusableWorkspace() {
    if (ControlledResourceServiceTest.reusableWorkspace == null) {
      ControlledResourceServiceTest.reusableWorkspace =
          workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
    }
    return reusableWorkspace;
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  public void createAiNotebookInstance() throws Exception {
    Workspace workspace = reusableWorkspace();

    String instanceId = "create-ai-notebook-instance";
    String location = "us-east1-b";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(location);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(location)
            .build();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateServiceAccountStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            DEFAULT_ROLES,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    Instance instance =
        notebooks
            .instances()
            .get(
                InstanceName.builder()
                    .projectId(workspace.getGcpCloudContext().get().getGcpProjectId())
                    .location(location)
                    .instanceId(instanceId)
                    .build())
            .execute();

    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_account"));
    // TODO(PF-469): Test that the user has permission to act as the service account.

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(),
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  public void createAiNotebookInstanceUndo() throws Exception {
    Workspace workspace = reusableWorkspace();

    String instanceId = "create-ai-notebook-instance-undo";
    String location = "us-east1-b";
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(location);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(location)
            .build();

    // Test idempotency of undo steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateServiceAccountStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            // Fail after the last step to test that everything is deleted on undo.
            .lastStepFailure(true)
            .undoStepFailures(retrySteps)
            .build());

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            DEFAULT_ROLES,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    GoogleJsonResponseException getException =
        assertThrows(
            GoogleJsonResponseException.class,
            () ->
                notebooks
                    .instances()
                    .get(
                        InstanceName.builder()
                            .projectId(projectId)
                            .location(location)
                            .instanceId(instanceId)
                            .build())
                    .execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), getException.getStatusCode());

    String serviceAccountId =
        stairwayComponent
            .get()
            .getFlightState(jobId)
            .getResultMap()
            .get()
            .get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class);
    // TODO(PF-469): Use service account "get" to check for existence instead of "delete" once we
    // have that method in CRL.
    GoogleJsonResponseException serviceAccountException =
        assertThrows(
            GoogleJsonResponseException.class,
            () ->
                crlService
                    .getIamCow()
                    .projects()
                    .serviceAccounts()
                    .delete(
                        "projects/"
                            + projectId
                            + "/serviceAccounts/"
                            + CreateServiceAccountStep.serviceAccountEmail(
                                serviceAccountId, projectId))
                    .execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), serviceAccountException.getStatusCode());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  public void createBqDatasetDo() throws Exception {
    Workspace workspace = reusableWorkspace();
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = "my_test_dataset";
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();

    // Test idempotency of dataset-specific step by retrying it once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource,
            creationParameters,
            Collections.emptyList(),
            userAccessUtils.defaultUserAuthRequest());
    assertEquals(resource, createdDataset);

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(cloudDataset.getLocation(), location);

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(),
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  public void createBqDatasetUndo() throws Exception {
    Workspace workspace = reusableWorkspace();
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = "my_undo_test_dataset";
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();

    // Test idempotency of datatset-specific undo step by retrying once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            // Fail after the last step to test that everything is deleted on undo.
            .lastStepFailure(true)
            .undoStepFailures(retrySteps)
            .build());

    // JobService throws a InvalidResultStateException when a synchronous flight fails without an
    // exception, which occurs when a flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createBqDataset(
                resource,
                creationParameters,
                Collections.emptyList(),
                userAccessUtils.defaultUserAuthRequest()));

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    GoogleJsonResponseException getException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bqCow.datasets().get(projectId, resource.getDatasetName()).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), getException.getStatusCode());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  // TODO(PF-469): Add a test verifying that a controlled resource can't be created for an instance
  // that already exists once we're ensuring cloud URI uniqueness for controlled resources.
}
