package bio.terra.workspace.service.resource.controlled;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import bio.terra.workspace.common.CloudUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.RetrieveNetworkNameStep;
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
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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

  private static Workspace reusableWorkspace;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private ControlledResourceMetadataManager controlledResourceMetadataManager;
  @Autowired private CrlService crlService;
  @Autowired private SamService samService;
  @Autowired private PetSaService petSaService;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private GcpCloudContextService gcpCloudContextService;

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

  /**
   * Checks whether the provided IamCow (with credentials) has permission to impersonate a provided
   * service account (via iam.serviceAccounts.actAs permission).
   */
  private static boolean canImpersonateSa(ServiceAccountName serviceAccountName, IamCow iam)
      throws IOException {
    TestIamPermissionsRequest actAsRequest =
        new TestIamPermissionsRequest()
            .setPermissions(Collections.singletonList("iam.serviceAccounts.actAs"));
    TestIamPermissionsResponse actAsResponse =
        iam.projects()
            .serviceAccounts()
            .testIamPermissions(serviceAccountName, actAsRequest)
            .execute();
    // If the result of the TestIamPermissions call has no permissions, the permissions field of the
    // response is null instead of an empty list. This is a quirk of GCP.
    return actAsResponse.getPermissions() != null;
  }

  /**
   * Retryable wrapper for {@code canImpersonateSa}.
   */
  private static void throwIfImpersonateSa(ServiceAccountName serviceAccountName, IamCow iam) throws IOException{
    if (canImpersonateSa(serviceAccountName, iam)) {
      throw new RuntimeException("User can still impersonate SA");
    }
  }

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
    UUID workspaceId = reusableWorkspace(user).getWorkspaceId();
    String instanceId = "create-ai-notebook-instance-do";
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource.Builder resourceBuilder =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId)
            .name(instanceId)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource = resourceBuilder.build();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveNetworkNameStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
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
            workspaceId, resource.getResourceId(), user.getAuthenticatedRequest()));

    InstanceName instanceName =
        resource.toInstanceName(
            workspaceService.getAuthorizedRequiredGcpProject(
                workspaceId, user.getAuthenticatedRequest()));
    Instance instance =
        crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();

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
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());
    // Revoke user's Pet SA access, if they have it. Because these tests re-use a common workspace,
    // the user may have pet SA access enabled prior to this test.
    petSaService.disablePetServiceAccountImpersonation(
        workspace.getWorkspaceId(), user.getEmail(), user.getAuthenticatedRequest());
    String serviceAccountEmail =
        samService.getOrCreatePetSaEmail(projectId, user.getAuthenticatedRequest());
    IamCow userIamCow = crlService.getIamCow(user.getAuthenticatedRequest());
    // Assert the user does not have access to their pet SA before the flight
    // Note this uses user credentials for the IAM cow to validate the user's access.
    assertFalse(canImpersonateSa(
        ServiceAccountName.builder().projectId(projectId).email(serviceAccountEmail).build(),
        userIamCow));
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    assertNotFound(resource.toInstanceName(projectId), crlService.getAIPlatformNotebooksCow());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest()));
    // This check relies on cloud IAM propagation and is sometimes delayed.
    CloudUtils.runWithRetryOnException(() -> throwIfImpersonateSa(
          ServiceAccountName.builder().projectId(projectId).email(serviceAccountEmail).build(),
          userIamCow));
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            reusableWorkspace(user).getWorkspaceId(), user.getAuthenticatedRequest());
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

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void createGetUpdateDeleteBqDataset() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());
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
        controlledResourceService.createBigQueryDataset(
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

    String newName = "NEW_createGetUpdateDeleteBqDataset";
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
        crlService.createWsmSaBigQueryCow().datasets().get(projectId, datasetId).execute();
    assertEquals(
        newDefaultTableLifetime * 1000L, updatedDatasetFromCloud.getDefaultTableExpirationMs());
    assertEquals(
        newDefaultPartitionLifetime * 1000L,
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    String datasetId = uniqueDatasetId();
    String location = "us-central1";
    Integer defaultTableLifetimeSec = 5900;
    Integer defaultPartitionLifetimeSec = 5901;
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(defaultTableLifetimeSec)
            .defaultPartitionLifetime(defaultPartitionLifetimeSec);
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
        controlledResourceService.createBigQueryDataset(
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(location, cloudDataset.getLocation());
    assertEquals(defaultTableLifetimeSec * 1000L, cloudDataset.getDefaultTableExpirationMs());
    assertEquals(
        defaultPartitionLifetimeSec * 1000L, cloudDataset.getDefaultPartitionExpirationMs());

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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

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
            controlledResourceService.createBigQueryDataset(
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

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
        controlledResourceService.createBigQueryDataset(
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

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
        controlledResourceService.createBigQueryDataset(
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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

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
        controlledResourceService.createBigQueryDataset(
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
    String newName = "NEW_updateBqDatasetDo";
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
    validateBigQueryDatasetCloudMetadata(
        projectId,
        createdDataset.getDatasetName(),
        location,
        newDefaultTableLifetime,
        newDefaultPartitionLifetime);

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
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    // create the dataset
    String datasetId = uniqueDatasetId();
    String location = "us-central1";
    Integer initialDefaultTableLifetime = 4800;
    Integer initialDefaultPartitionLifetime = 4801;
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(initialDefaultTableLifetime)
            .defaultPartitionLifetime(initialDefaultPartitionLifetime);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBigQueryDataset(
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
                "NEW_updateBqDatasetUndo",
                "new resource description"));

    // check the properties stored on the cloud were not updated
    validateBigQueryDatasetCloudMetadata(
        projectId,
        createdDataset.getDatasetName(),
        location,
        initialDefaultTableLifetime,
        initialDefaultPartitionLifetime);

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

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateBqDatasetWithUndefinedExpirationTimes() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    // create the dataset, with expiration times initially defined
    String datasetId = uniqueDatasetId();
    String location = "us-central1";
    Integer initialDefaultTableLifetime = 4800;
    Integer initialDefaultPartitionLifetime = 4801;
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(initialDefaultTableLifetime)
            .defaultPartitionLifetime(initialDefaultPartitionLifetime);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService.createBigQueryDataset(
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());

    // check the expiration times stored on the cloud are defined
    validateBigQueryDatasetCloudMetadata(
        projectId,
        createdDataset.getDatasetName(),
        location,
        initialDefaultTableLifetime,
        initialDefaultPartitionLifetime);

    // make an update request to set the expiration times to undefined values
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(0)
            .defaultPartitionLifetime(0);
    controlledResourceService.updateBqDataset(
        resource, updateParameters, user.getAuthenticatedRequest(), null, null);

    // check the expiration times stored on the cloud are now undefined
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, null, null);

    // update just one expiration time back to a defined value
    Integer newDefaultTableLifetime = 3600;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters().defaultTableLifetime(newDefaultTableLifetime);
    controlledResourceService.updateBqDataset(
        resource, updateParameters, user.getAuthenticatedRequest(), null, null);

    // check there is one defined and one undefined expiration value
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, newDefaultTableLifetime, null);

    // update the other expiration time back to a defined value
    Integer newDefaultPartitionLifetime = 3601;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    controlledResourceService.updateBqDataset(
        resource, updateParameters, user.getAuthenticatedRequest(), null, null);

    // check the expiration times stored on the cloud are both defined again
    validateBigQueryDatasetCloudMetadata(
        projectId,
        createdDataset.getDatasetName(),
        location,
        newDefaultTableLifetime,
        newDefaultPartitionLifetime);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateBqDatasetWithInvalidExpirationTimes() throws Exception {
    UserAccessUtils.TestUser user = userAccessUtils.defaultUser();
    Workspace workspace = reusableWorkspace(user);
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    // create the dataset, with expiration times initially undefined
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
        controlledResourceService.createBigQueryDataset(
            resource, creationParameters, Collections.emptyList(), user.getAuthenticatedRequest());

    // make an update request to set the table expiration time to an invalid value (<3600)
    final ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(3000)
            .defaultPartitionLifetime(3601);
    assertThrows(
        BadRequestException.class,
        () ->
            controlledResourceService.updateBqDataset(
                resource, updateParameters, user.getAuthenticatedRequest(), null, null));

    // check the expiration times stored on the cloud are still undefined, because the update above
    // failed
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, null, null);

    // make another update request to set the partition expiration time to an invalid value (<0)
    final ApiGcpBigQueryDatasetUpdateParameters updateParameters2 =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(3600)
            .defaultPartitionLifetime(-2);
    assertThrows(
        BadRequestException.class,
        () ->
            controlledResourceService.updateBqDataset(
                resource, updateParameters2, user.getAuthenticatedRequest(), null, null));

    // check the expiration times stored on the cloud are still undefined, because the update above
    // failed
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, null, null);
  }

  /**
   * Lookup the location and expiration times stored on the cloud for a BigQuery dataset, and assert
   * they match the given values.
   */
  private void validateBigQueryDatasetCloudMetadata(
      String projectId,
      String datasetId,
      String location,
      Integer defaultTableExpirationSec,
      Integer defaultPartitionExpirationSec)
      throws IOException {
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset = bqCow.datasets().get(projectId, datasetId).execute();

    assertEquals(location, cloudDataset.getLocation());

    if (defaultTableExpirationSec == null) {
      assertNull(cloudDataset.getDefaultTableExpirationMs());
    } else {
      assertEquals(defaultTableExpirationSec * 1000, cloudDataset.getDefaultTableExpirationMs());
    }

    if (defaultPartitionExpirationSec == null) {
      assertNull(cloudDataset.getDefaultPartitionExpirationMs());
    } else {
      assertEquals(
          defaultPartitionExpirationSec * 1000, cloudDataset.getDefaultPartitionExpirationMs());
    }
  }
}
