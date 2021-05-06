package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
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
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.ServiceAccountPolicyStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteSamResourceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.DeleteServiceAccountStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.RetrieveNotebookServiceAccountStep;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
  /** The default GCP location to create notebooks for this test. */
  private static final String DEFAULT_NOTEBOOK_LOCATION = "us-east1-b";

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
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createAiNotebookInstanceDo() throws Exception {
    Workspace workspace = reusableWorkspace();
    String instanceId = "create-ai-notebook-instance-do";
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource.Builder resourceBuilder =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .name(instanceId)
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource = resourceBuilder.build();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveNetworkNameStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateServiceAccountStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(ServiceAccountPolicyStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(NotebookCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
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

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(),
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest()));

    // TODO(PF-643): this should happen inside WSM.
    // Waiting 15s for permissions to propagate
    TimeUnit.SECONDS.sleep(15);

    AIPlatformNotebooksCow userNotebooks =
        crlService.createAIPlatformNotebooksCow(userAccessUtils.defaultUserAuthRequest());
    InstanceName instanceName =
        resource.toInstanceName(workspace.getGcpCloudContext().get().getGcpProjectId());
    Instance instance =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(resource.toInstanceName(workspace.getGcpCloudContext().get().getGcpProjectId()))
            .execute();

    // Test that the user has permissions from WRITER roles.
    List<String> expectedWriterPermissions =
        ImmutableList.of(
            "notebooks.instances.get",
            "notebooks.instances.list",
            "notebooks.instances.reset",
            "notebooks.instances.setAccelerator",
            "notebooks.instances.setMachineType",
            "notebooks.instances.start",
            "notebooks.instances.stop",
            "notebooks.instances.use");

    try {
      crlService
          .getAIPlatformNotebooksCow()
          .instances()
          .testIamPermissions(
              instanceName,
              new com.google.api.services.notebooks.v1.model.TestIamPermissionsRequest()
                  .setPermissions(expectedWriterPermissions))
          .execute();
    } catch (Exception e) {
      // DO NOT SUBMIT
      System.out.println(e);
    }
    //
    //    assertThat(
    //        crlService
    //            .getAIPlatformNotebooksCow()
    //            .instances()
    //            .testIamPermissions(
    //                instanceName,
    //                new com.google.api.services.notebooks.v1.model.TestIamPermissionsRequest()
    //                    .setPermissions(expectedWriterPermissions))
    //            .execute()
    //            .getPermissions(),
    //        Matchers.containsInAnyOrder(expectedWriterPermissions.toArray()));

    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    ServiceAccountName serviceAccountName =
        ServiceAccountName.builder()
            .projectId(instanceName.projectId())
            .email(instance.getServiceAccount())
            .build();
    IamCow userIam = crlService.createIamCow(userAccessUtils.defaultUserAuthRequest());
    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    assertThat(
        userIam
            .projects()
            .serviceAccounts()
            .testIamPermissions(
                serviceAccountName,
                new TestIamPermissionsRequest().setPermissions(List.of(actAsPermission)))
            .execute()
            .getPermissions(),
        Matchers.contains(actAsPermission));

    // Creating a controlled resource with a duplicate underlying notebook instance is not allowed.
    ControlledAiNotebookInstanceResource duplicateResource =
        resourceBuilder
            .resourceId(UUID.randomUUID())
            .name("new-name-same-notebook-instance")
            .build();
    String duplicateResourceJobId =
        controlledResourceService.createAiNotebookInstance(
            duplicateResource,
            creationParameters,
            DEFAULT_ROLES,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            userAccessUtils.defaultUserAuthRequest());

    jobService.waitForJob(duplicateResourceJobId);
    JobService.JobResultOrException<ControlledAiNotebookInstanceResource> duplicateJobResult =
        jobService.retrieveJobResult(
            duplicateResourceJobId,
            ControlledAiNotebookInstanceResource.class,
            userAccessUtils.defaultUserAuthRequest());
    assertEquals(duplicateJobResult.getException().getClass(), DuplicateResourceException.class);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createAiNotebookInstanceUndo() throws Exception {
    Workspace workspace = reusableWorkspace();
    String instanceId = "create-ai-notebook-instance-undo";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .name(instanceId)
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
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

    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();
    assertNotFound(resource.toInstanceName(projectId), crlService.getAIPlatformNotebooksCow());

    String serviceAccountId =
        stairwayComponent
            .get()
            .getFlightState(jobId)
            .getResultMap()
            .get()
            .get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class);
    assertNotFound(
        ServiceAccountName.builder()
            .projectId(projectId)
            .email(ServiceAccountName.emailFromAccountId(serviceAccountId, projectId))
            .build(),
        crlService.getIamCow());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteAiNotebookInstanceDo() throws Exception {
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-do");
    String projectId = reusableWorkspace().getGcpCloudContext().get().getGcpProjectId();
    InstanceName instanceName = resource.toInstanceName(projectId);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    Instance instance = notebooks.instances().get(instanceName).execute();
    ServiceAccountName serviceAccountName =
        ServiceAccountName.builder()
            .projectId(projectId)
            .email(instance.getServiceAccount())
            .build();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveNotebookServiceAccountStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        DeleteAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteServiceAccountStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        userAccessUtils.defaultUserAuthRequest());
    assertNotFound(instanceName, notebooks);
    assertNotFound(serviceAccountName, crlService.getIamCow());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteAiNotebookInstanceUndoIsDismalFailure() throws Exception {
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-undo");

    // Test that trying to undo a notebook deletion is a dismal failure. We cannot undo deletion.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.deleteControlledResourceSync(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  /** Create a controlled AI Notebook instance with default private settings. */
  private ControlledAiNotebookInstanceResource createDefaultPrivateAiNotebookInstance(
      String instanceId) {
    Workspace workspace = reusableWorkspace();
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .name(instanceId)
            .workspaceId(workspace.getWorkspaceId())
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .build();

    String createJobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            DEFAULT_ROLES,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            null,
            userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(createJobId);
    return jobService
        .retrieveJobResult(
            createJobId,
            ControlledAiNotebookInstanceResource.class,
            userAccessUtils.defaultUserAuthRequest())
        .getResult();
  }

  private static void assertNotFound(InstanceName instanceName, AIPlatformNotebooksCow notebooks) {
    GoogleJsonResponseException exception =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> notebooks.instances().get(instanceName).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), exception.getStatusCode());
  }

  private static void assertNotFound(ServiceAccountName serviceAccountName, IamCow iam) {
    GoogleJsonResponseException exception =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> iam.projects().serviceAccounts().get(serviceAccountName).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), exception.getStatusCode());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createGetUpdateDeleteBqDataset() throws Exception {
    Workspace workspace = reusableWorkspace();

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
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource,
            creationParameters,
            Collections.emptyList(),
            userAccessUtils.defaultUserAuthRequest());
    assertEquals(resource, createdDataset);

    ControlledBigQueryDatasetResource fetchedDataset =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest())
            .castToBigQueryDatasetResource();
    assertEquals(resource, fetchedDataset);

    String newName = "newResourceName";
    String newDescription = "new resource description";
    controlledResourceService.updateControlledResourceMetadata(
        workspace.getWorkspaceId(),
        resource.getResourceId(),
        newName,
        newDescription,
        userAccessUtils.defaultUserAuthRequest());

    ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest())
            .castToBigQueryDatasetResource();
    assertEquals(newName, updatedResource.getName());
    assertEquals(newDescription, updatedResource.getDescription());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        userAccessUtils.defaultUserAuthRequest());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
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
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
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

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteBqDatasetDo() throws Exception {
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

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource,
            creationParameters,
            Collections.emptyList(),
            userAccessUtils.defaultUserAuthRequest());
    assertEquals(resource, createdDataset);

    // Test idempotency of delete by retrying steps once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteSamResourceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    // Do not test lastStepFailure, as this flight has no undo steps, only dismal failure.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        userAccessUtils.defaultUserAuthRequest());

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

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteBqDatasetUndo() throws Exception {
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

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource,
            creationParameters,
            Collections.emptyList(),
            userAccessUtils.defaultUserAuthRequest());
    assertEquals(resource, createdDataset);

    // None of the steps on this flight are undoable, so even with lastStepFailure set to true we
    // should expect the resource to really be deleted.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());

    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.deleteControlledResourceSync(
                resource.getWorkspaceId(),
                resource.getResourceId(),
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
}
