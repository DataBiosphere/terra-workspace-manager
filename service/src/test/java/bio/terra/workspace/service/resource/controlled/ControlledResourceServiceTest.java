package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
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
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
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
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveBigQueryDatasetCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateBigQueryDatasetStep;
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
  @Autowired private ControlledResourceMetadataManager controlledResourceMetadataManager;
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
   *
   * @param user
   */
  private Workspace reusableWorkspace(UserAccessUtils.TestUser user) {
    if (ControlledResourceServiceTest.reusableWorkspace == null) {
      ControlledResourceServiceTest.reusableWorkspace =
          workspaceUtils.createWorkspaceWithGcpContext(user.getAuthenticatedRequest());
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
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String instanceId = "create-ai-notebook-instance-do";
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource.Builder resourceBuilder =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .name(instanceId)
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
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest()));

    // TODO(PF-643): this should happen inside WSM.
    // Waiting 15s for permissions to propagate
    TimeUnit.SECONDS.sleep(15);

    InstanceName instanceName =
        resource.toInstanceName(workspace.getGcpCloudContext().get().getGcpProjectId());
    Instance instance =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(resource.toInstanceName(workspace.getGcpCloudContext().get().getGcpProjectId()))
            .execute();

    // Test that the user has permissions from WRITER roles on the notebooks instance. Only notebook
    // instance level permissions can be checked on the notebook instance test IAM permissions
    // endpoint, so no "notebooks.instances.list" permission as that's project level.
    List<String> expectedWriterPermissions =
        ImmutableList.of(
            "notebooks.instances.get",
            "notebooks.instances.reset",
            "notebooks.instances.setAccelerator",
            "notebooks.instances.setMachineType",
            "notebooks.instances.start",
            "notebooks.instances.stop",
            "notebooks.instances.use");
    assertThat(
        AIPlatformNotebooksCow.create(crlService.getClientConfig(), user.getGoogleCredentials())
            .instances()
            .testIamPermissions(
                instanceName,
                new com.google.api.services.notebooks.v1.model.TestIamPermissionsRequest()
                    .setPermissions(expectedWriterPermissions))
            .execute()
            .getPermissions(),
        Matchers.containsInAnyOrder(expectedWriterPermissions.toArray()));

    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    ServiceAccountName serviceAccountName =
        ServiceAccountName.builder()
            .projectId(instanceName.projectId())
            .email(instance.getServiceAccount())
            .build();
    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    assertThat(
        IamCow.create(crlService.getClientConfig(), user.getGoogleCredentials())
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
            user.getAuthenticatedRequest());

    jobService.waitForJob(duplicateResourceJobId);
    JobService.JobResultOrException<ControlledAiNotebookInstanceResource> duplicateJobResult =
        jobService.retrieveJobResult(
            duplicateResourceJobId,
            ControlledAiNotebookInstanceResource.class,
            user.getAuthenticatedRequest());
    assertEquals(DuplicateResourceException.class, duplicateJobResult.getException().getClass());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createAiNotebookInstanceUndo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String instanceId = "create-ai-notebook-instance-undo";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .name(instanceId)
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
            user.getAuthenticatedRequest());
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
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createAiNotebookInstanceNoWriterRoleThrowsBadRequest() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String instanceId = "create-ai-notebook-instance-shared";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .name(instanceId)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .build();

    // Shared notebooks not yet implemented.
    // Private IAM roles must include writer role.
    List<ControlledResourceIamRole> noWriterRoles =
        // Need to be careful about what subclass of List gets put in a FlightMap.
        Stream.of(ControlledResourceIamRole.READER).collect(Collectors.toList());
    BadRequestException noWriterException =
        assertThrows(
            BadRequestException.class,
            () ->
                controlledResourceService.createAiNotebookInstance(
                    resource,
                    creationParameters,
                    noWriterRoles,
                    new ApiJobControl().id(UUID.randomUUID().toString()),
                    "fakeResultPath",
                    user.getAuthenticatedRequest()));
    assertEquals(
        "A private, controlled AI Notebook instance must have the writer role or else it is not useful.",
        noWriterException.getMessage());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteAiNotebookInstanceDo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-do", user);
    String projectId = reusableWorkspace(user).getGcpCloudContext().get().getGcpProjectId();
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
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());
    assertNotFound(instanceName, notebooks);
    assertNotFound(serviceAccountName, crlService.getIamCow());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteAiNotebookInstanceUndoIsDismalFailure() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-undo", user);

    // Test that trying to undo a notebook deletion is a dismal failure. We cannot undo deletion.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.deleteControlledResourceSync(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest()));
  }

  /** Create a controlled AI Notebook instance with default private settings. */
  private ControlledAiNotebookInstanceResource createDefaultPrivateAiNotebookInstance(
      String instanceId, UserAccessUtils.TestUser user) {
    Workspace workspace = reusableWorkspace(user);
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .name(instanceId)
            .workspaceId(workspace.getWorkspaceId())
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
            user.getAuthenticatedRequest());
    jobService.waitForJob(createJobId);
    JobService.JobResultOrException<ControlledAiNotebookInstanceResource> creationResult =
        jobService.retrieveJobResult(
            createJobId,
            ControlledAiNotebookInstanceResource.class,
            user.getAuthenticatedRequest());
    assertNull(creationResult.getException(), "Error creating controlled AI notebook instance");
    assertNotNull(
        creationResult.getResult(), "Unexpected null created controlled AI notebook instance");
    return creationResult.getResult();
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
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);

    String datasetId = "my_test_dataset";
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(4800)
            .defaultPartitionLifetime(4801);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    ControlledBigQueryDatasetResource fetchedDataset =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest())
            .castToBigQueryDatasetResource();
    assertEquals(resource, fetchedDataset);

    String newName = "newResourceName";
    String newDescription = "new resource description";
    Integer newDefaultTableLifetime = 3600;
    Integer newDefaultPartitionLifetime = 3601;
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(newDefaultTableLifetime)
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    controlledResourceService.updateBqDataset(
        fetchedDataset, updateParameters, user.getAuthenticatedRequest(), newName, newDescription);

    ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest())
            .castToBigQueryDatasetResource();
    assertEquals(newName, updatedResource.getName());
    assertEquals(newDescription, updatedResource.getDescription());

    Dataset updatedDatasetFromCloud =
        crlService
            .createWsmSaBigQueryCow()
            .datasets()
            .get(workspace.getGcpCloudContext().get().getGcpProjectId(), datasetId)
            .execute();
    assertEquals(
        newDefaultTableLifetime * 1000, updatedDatasetFromCloud.getDefaultTableExpirationMs());
    assertEquals(
        newDefaultPartitionLifetime * 1000,
        updatedDatasetFromCloud.getDefaultPartitionExpirationMs());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createBqDatasetDo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(5900)
            .defaultPartitionLifetime(5901);
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
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(location, cloudDataset.getLocation());
    assertEquals(5900 * 1000, cloudDataset.getDefaultTableExpirationMs());
    assertEquals(5901 * 1000, cloudDataset.getDefaultPartitionExpirationMs());

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest()));
  }

  /** Create a dataset name with a random 4-digit (rarely 5) suffix */
  private String uniqueDatasetId() {
    return "my_test_dataset_" + (int) (Math.floor(Math.random() * 10000));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createBqDatasetUndo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = uniqueDatasetId();
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
                user.getAuthenticatedRequest()));

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
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteBqDatasetDo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = uniqueDatasetId();
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
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
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
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());

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
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void deleteBqDatasetUndo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    String datasetId = uniqueDatasetId();
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
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
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
                user.getAuthenticatedRequest()));

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
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateBqDatasetDo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    // create the dataset
    String datasetId = uniqueDatasetId();
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
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    // Test idempotency of dataset-specific steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveBigQueryDatasetCloudAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    // update the dataset
    String newName = "newResourceName";
    String newDescription = "new resource description";
    Integer newDefaultTableLifetime = 3600;
    Integer newDefaultPartitionLifetime = 3601;
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(newDefaultTableLifetime)
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    controlledResourceService.updateBqDataset(
        resource, updateParameters, user.getAuthenticatedRequest(), newName, newDescription);

    // check the properties stored on the cloud were updated
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(location, cloudDataset.getLocation());
    assertEquals(newDefaultTableLifetime * 1000, cloudDataset.getDefaultTableExpirationMs());
    assertEquals(
        newDefaultPartitionLifetime * 1000, cloudDataset.getDefaultPartitionExpirationMs());

    // check the properties stored in WSM were updated
    ControlledBigQueryDatasetResource fetchedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest())
            .castToBigQueryDatasetResource();
    assertEquals(newName, fetchedResource.getName());
    assertEquals(newDescription, fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateBqDatasetUndo() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId = workspace.getGcpCloudContext().get().getGcpProjectId();

    // create the dataset
    String datasetId = uniqueDatasetId();
    String location = "us-central1";
    Integer defaultPartitionLifetime = 6400;
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(null);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBqDataset(
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    // Test idempotency of dataset-specific steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveBigQueryDatasetCloudAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            // Fail after the last step to test that everything is back to the original on undo.
            .lastStepFailure(true)
            .undoStepFailures(retrySteps)
            .build());

    // update the dataset
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(3600)
            .defaultPartitionLifetime(3601);

    // JobService throws a InvalidResultStateException when a synchronous flight fails without an
    // exception, which occurs when a flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.updateBqDataset(
                resource,
                updateParameters,
                user.getAuthenticatedRequest(),
                "newResourceName",
                "new resource description"));

    // check the properties stored on the cloud were not updated
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(location, cloudDataset.getLocation());
    assertNull(cloudDataset.getDefaultTableExpirationMs());
    assertEquals(defaultPartitionLifetime * 1000, cloudDataset.getDefaultPartitionExpirationMs());

    // check the properties stored in WSM were not updated
    ControlledBigQueryDatasetResource fetchedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest())
            .castToBigQueryDatasetResource();
    assertEquals(resource.getName(), fetchedResource.getName());
    assertEquals(resource.getDescription(), fetchedResource.getDescription());
  }
}
