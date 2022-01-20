package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.resource.controlled.ResourceConstant.DEFAULT_REGION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.CloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.petserviceaccount.model.UserWithPetSa;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GcsBucketCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveBigQueryDatasetCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateGcsBucketStep;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.Alpha1Service;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.cloud.storage.BucketInfo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledResourceServiceTest extends BaseConnectedTest {
  /** The default roles to use when creating user private AI notebook instance resources */
  private static final ControlledResourceIamRole DEFAULT_ROLE = ControlledResourceIamRole.WRITER;
  /** The default GCP location to create notebooks for this test. */
  private static final String DEFAULT_NOTEBOOK_LOCATION = "us-east1-b";

  private static Workspace reusableWorkspace;
  private Workspace workspace;
  private UserAccessUtils.TestUser user;
  private String projectId;
  @Autowired private Alpha1Service alpha1Service;
  @Autowired private FeatureConfiguration features;
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

  /** Retryable wrapper for {@code canImpersonateSa}. */
  private static void throwIfImpersonateSa(ServiceAccountName serviceAccountName, IamCow iam)
      throws IOException {
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
   * Set up default values for user, workspace, and projectId for tests to use. By default, this
   * will point to the reusable workspace created by {@code reusableWorkspace}.
   */
  @BeforeEach
  public void setupUserAndWorkspace() {
    user = userAccessUtils.defaultUser();
    workspace = reusableWorkspace(user);
    projectId =
        workspaceService.getAuthorizedRequiredGcpProject(
            workspace.getWorkspaceId(), user.getAuthenticatedRequest());
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    // Exercise enumeration by dumping after each
    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(
        alpha1Service, workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    jobService.setFlightDebugInfoForTest(null);
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  private void cleanUpSharedWorkspace() {
    user = userAccessUtils.defaultUser();
    workspaceService.deleteWorkspace(
        reusableWorkspace.getWorkspaceId(), user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceDo() throws Exception {
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
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .assignedUser(user.getEmail())
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
            DEFAULT_ROLE,
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
            DEFAULT_ROLE,
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
  void createAiNotebookInstanceUndo() throws Exception {
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
            .assignedUser(user.getEmail())
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
            DEFAULT_ROLE,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    // Revoke user's Pet SA access, if they have it. Because these tests re-use a common workspace,
    // the user may have pet SA access enabled prior to this test.
    String serviceAccountEmail =
        samService.getOrCreatePetSaEmail(projectId, user.getAuthenticatedRequest());
    UserWithPetSa userAndPet = new UserWithPetSa(user.getEmail(), serviceAccountEmail);
    petSaService.disablePetServiceAccountImpersonation(workspace.getWorkspaceId(), userAndPet);
    IamCow userIamCow = crlService.getIamCow(user.getAuthenticatedRequest());
    // Assert the user does not have access to their pet SA before the flight
    // Note this uses user credentials for the IAM cow to validate the user's access.
    assertFalse(
        canImpersonateSa(
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
    CloudUtils.runWithRetryOnException(
        () ->
            throwIfImpersonateSa(
                ServiceAccountName.builder()
                    .projectId(projectId)
                    .email(serviceAccountEmail)
                    .build(),
                userIamCow));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceNoWriterRoleThrowsBadRequest() throws Exception {
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
            .assignedUser(user.getEmail())
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .build();

    // Shared notebooks not yet implemented.
    // Private IAM roles must include writer role.
    ControlledResourceIamRole notWriter = ControlledResourceIamRole.READER;
    BadRequestException noWriterException =
        assertThrows(
            BadRequestException.class,
            () ->
                controlledResourceService.createAiNotebookInstance(
                    resource,
                    creationParameters,
                    notWriter,
                    new ApiJobControl().id(UUID.randomUUID().toString()),
                    "fakeResultPath",
                    user.getAuthenticatedRequest()));
    assertEquals(
        "A private, controlled AI Notebook instance must have the writer or editor role or else it is not useful.",
        noWriterException.getMessage());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteAiNotebookInstanceDo() throws Exception {
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-do", user);
    InstanceName instanceName = resource.toInstanceName(projectId);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());
    assertNotFound(instanceName, notebooks);
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
  void deleteAiNotebookInstanceUndoIsDismalFailure() throws Exception {
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
            .assignedUser(user.getEmail())
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .build();

    String createJobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            DEFAULT_ROLE,
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
  void createGetUpdateDeleteBqDataset() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
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

    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(
        alpha1Service, workspace.getWorkspaceId(), user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createBqDatasetDo() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
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
  void createBqDatasetUndo() throws Exception {
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

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createBigQueryDataset(
                resource, creationParameters, null, user.getAuthenticatedRequest()));

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
  void deleteBqDatasetDo() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
    assertEquals(resource, createdDataset);

    // Test idempotency of delete by retrying steps once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
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
  void deleteBqDatasetUndo() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
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
  void updateBqDatasetDo() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
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
  void updateBqDatasetUndo() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());
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

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
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
  void updateBqDatasetWithUndefinedExpirationTimes() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());

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
  void updateBqDatasetWithInvalidExpirationTimes() throws Exception {
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
            resource, creationParameters, null, user.getAuthenticatedRequest());

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

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketDo() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();

    // Test idempotency of bucket-specific steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcsBucketCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    ControlledGcsBucketResource createdBucket =
        controlledResourceService.createBucket(
            resource,
            ControlledResourceFixtures.getGoogleBucketCreationParameters(),
            null,
            user.getAuthenticatedRequest());
    assertEquals(resource, createdBucket);

    StorageCow storageCow = crlService.createStorageCow(projectId);
    BucketInfo cloudBucket = storageCow.get(resource.getBucketName()).getBucketInfo();
    assertEquals(DEFAULT_REGION, cloudBucket.getLocation().toLowerCase());
    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketDo_invalidBucketName_throwsBadRequestException() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .bucketName("192.168.5.4")
            .build();

    assertThrows(
        BadRequestException.class,
        () ->
            controlledResourceService.createBucket(
                resource,
                ControlledResourceFixtures.getGoogleBucketCreationParameters(),
                null,
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketUndo() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();

    // Test idempotency of bucket-specific undo steps by retrying them once. Fail at the end of
    // the flight to ensure undo steps work properly.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcsBucketCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build());
    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createBucket(
                resource,
                ControlledResourceFixtures.getGoogleBucketCreationParameters(),
                null,
                user.getAuthenticatedRequest()));

    // Validate the bucket does not exist.
    StorageCow storageCow = crlService.createStorageCow(projectId);
    assertNull(storageCow.get(resource.getBucketName()));

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
  void deleteGcsBucketDo() throws Exception {
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(workspace, user);

    // Test idempotency of bucket-specific delete step by retrying it once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            new ApiJobControl().id(UUID.randomUUID().toString()),
            workspace.getWorkspaceId(),
            createdBucket.getResourceId(),
            "fake result path",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // Validate the bucket does not exist.
    StorageCow storageCow = crlService.createStorageCow(projectId);
    assertNull(storageCow.get(createdBucket.getBucketName()));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(),
                createdBucket.getResourceId(),
                user.getAuthenticatedRequest()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGcsBucketDo() throws Exception {
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(workspace, user);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveControlledResourceMetadataStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledResourceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveGcsBucketCloudAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    // update the bucket
    String newName = "NEW_bucketname";
    String newDescription = "new resource description";
    controlledResourceService.updateGcsBucket(
        createdBucket,
        ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_2,
        user.getAuthenticatedRequest(),
        newName,
        newDescription);

    // check the properties stored in WSM were updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                createdBucket.getResourceId(),
                user.getAuthenticatedRequest())
            .castToGcsBucketResource();
    assertEquals(newName, fetchedResource.getName());
    assertEquals(newDescription, fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGcsBucketUndo() throws Exception {
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(workspace, user);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveControlledResourceMetadataStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledResourceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveGcsBucketCloudAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build());

    // update the bucket
    String newName = "NEW_bucketname";
    String newDescription = "new resource description";
    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.updateGcsBucket(
                createdBucket,
                ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_2,
                user.getAuthenticatedRequest(),
                newName,
                newDescription));

    // check the properties stored on the cloud were not updated
    BucketInfo updatedBucket =
        crlService.createStorageCow(projectId).get(createdBucket.getBucketName()).getBucketInfo();
    ApiGcpGcsBucketUpdateParameters cloudParameters =
        GcsApiConversions.toUpdateParameters(updatedBucket);
    assertNotEquals(ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_2, cloudParameters);

    // check the properties stored in WSM were not updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(
                workspace.getWorkspaceId(),
                createdBucket.getResourceId(),
                user.getAuthenticatedRequest())
            .castToGcsBucketResource();
    assertEquals(createdBucket.getName(), fetchedResource.getName());
    assertEquals(createdBucket.getDescription(), fetchedResource.getDescription());
  }

  /**
   * Creates a user-shared controlled GCS bucket in the provided workspace, using the credentials of
   * the provided user. This uses the default bucket creation parameters from {@code
   * ControlledResourceFixtures}.
   */
  private ControlledGcsBucketResource createDefaultSharedGcsBucket(
      Workspace workspace, UserAccessUtils.TestUser user) {
    ControlledGcsBucketResource originalResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();

    ControlledGcsBucketResource createdBucket =
        controlledResourceService.createBucket(
            originalResource,
            ControlledResourceFixtures.getGoogleBucketCreationParameters(),
            null,
            user.getAuthenticatedRequest());
    assertEquals(originalResource, createdBucket);
    return createdBucket;
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
