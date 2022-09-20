package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AI_NOTEBOOK_PREV_PARAMETERS;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AI_NOTEBOOK_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant.DEFAULT_REGION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.CloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.RetrieveAiNotebookResourceAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.UpdateAiNotebookAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.DeleteBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.RetrieveBigQueryDatasetCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.UpdateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.CreateGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.DeleteGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsBucketCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.UpdateGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResourceType;
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
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
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
  @Autowired private CliConfiguration cliConfiguration;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private FeatureConfiguration features;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private PetSaService petSaService;
  @Autowired private SamService samService;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;

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
    projectId = gcpCloudContextService.getRequiredGcpProject(workspace.getWorkspaceId());
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
    workspaceService.deleteWorkspace(reusableWorkspace, user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceDo() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();
    String workspaceUserFacingId = workspace.getUserFacingId();
    var instanceId = "create-ai-notebook-instance-do";
    var serverName = "verily-autopush";
    cliConfiguration.setServerName(serverName);
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);

    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspaceUuid, "initial-notebook-name", instanceId);

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
        controlledResourceService.getControlledResource(workspaceUuid, resource.getResourceId()));

    InstanceName instanceName =
        resource.toInstanceName(gcpCloudContextService.getRequiredGcpProject(workspaceUuid));
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
        containsInAnyOrder(expectedWriterPermissions.toArray()));

    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-cli-server", serverName));
    assertThat(
        instance.getMetadata(), Matchers.hasEntry("terra-workspace-id", workspaceUserFacingId));
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
        makeNotebookTestResource(workspaceUuid, "new-name-same-notebook-instance", instanceId);
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
            duplicateResourceJobId, ControlledAiNotebookInstanceResource.class);
    assertEquals(DuplicateResourceException.class, duplicateJobResult.getException().getClass());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceUndo() throws Exception {
    String instanceId = "create-ai-notebook-instance-undo";
    String name = "create-ai-notebook-instance-undo-name";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspace.getWorkspaceId(), name, instanceId);

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

    // Revoke user's Pet SA access, if they have it. Because these tests re-use a common workspace,
    // the user may have pet SA access enabled prior to this test.
    String serviceAccountEmail =
        samService.getOrCreatePetSaEmail(
            projectId, user.getAuthenticatedRequest().getRequiredToken());
    petSaService.disablePetServiceAccountImpersonation(
        workspace.getWorkspaceId(), user.getEmail(), user.getAuthenticatedRequest());
    IamCow userIamCow = crlService.getIamCow(user.getAuthenticatedRequest());
    // Assert the user does not have access to their pet SA before the flight
    // Note this uses user credentials for the IAM cow to validate the user's access.
    CloudUtils.runWithRetryOnException(
        () ->
            throwIfImpersonateSa(
                ServiceAccountName.builder()
                    .projectId(projectId)
                    .email(serviceAccountEmail)
                    .build(),
                userIamCow));

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
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    assertNotFound(resource.toInstanceName(projectId), crlService.getAIPlatformNotebooksCow());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(), resource.getResourceId()));
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
  void updateAiNotebookResourceDo() throws InterruptedException, IOException {
    var instanceId = "update-ai-notebook-instance-do";
    var name = "update-ai-notebook-instance-do-name";
    var newName = "update-ai-notebook-instance-do-name-NEW";
    var newDescription = "new description for update-ai-notebook-instance-do-name-NEW";

    var creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspace.getWorkspaceId(), name, instanceId);
    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledAiNotebookInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(fetchedInstance.toInstanceName(projectId))
            .execute();
    var metadata = instanceFromCloud.getMetadata();

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveControlledResourceMetadataStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledResourceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    controlledResourceService.updateAiNotebookInstance(
        fetchedInstance,
        AI_NOTEBOOK_UPDATE_PARAMETERS,
        newName,
        newDescription,
        user.getAuthenticatedRequest());

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(newName, updatedInstance.getName());
    assertEquals(newDescription, updatedInstance.getDescription());
    // cloud notebook attributes are updated.
    var updatedInstanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName(projectId))
            .execute();
    // Merge metadata from AI_NOTEBOOK_UPDATE_PARAMETERS to metadata.
    AI_NOTEBOOK_UPDATE_PARAMETERS
        .getMetadata()
        .forEach(
            (key, value) ->
                metadata.merge(
                    key, value, (v1, v2) -> v1.equalsIgnoreCase(v2) ? v1 : v1 + "," + v2));
    for (var entrySet : AI_NOTEBOOK_UPDATE_PARAMETERS.getMetadata().entrySet()) {
      assertEquals(
          entrySet.getValue(), updatedInstanceFromCloud.getMetadata().get(entrySet.getKey()));
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateAiNotebookResourceDo_nameAndDescriptionOnly()
      throws InterruptedException, IOException {
    var instanceId = "update-ai-notebook-instance-do-name-and-description-only";
    var name = "update-ai-notebook-instance-do-name-and-description-only";
    var newName = "update-ai-notebook-instance-do-name-and-description-only-NEW";
    var newDescription =
        "new description for update-ai-notebook-instance-do-name-and-description-only-NEW";

    var creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspace.getWorkspaceId(), name, instanceId);
    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledAiNotebookInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(fetchedInstance.toInstanceName(projectId))
            .execute();
    var metadata = instanceFromCloud.getMetadata();

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveControlledResourceMetadataStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledResourceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    controlledResourceService.updateAiNotebookInstance(
        fetchedInstance, null, newName, newDescription, user.getAuthenticatedRequest());

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(newName, updatedInstance.getName());
    assertEquals(newDescription, updatedInstance.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateAiNotebookResourceUndo() throws InterruptedException, IOException {
    String instanceId = "update-ai-notebook-instance-undo";
    String name = "update-ai-notebook-instance-undo-name";
    String newName = "update-ai-notebook-instance-undo-name-NEW";
    String newDescription = "new description for update-ai-notebook-instance-undo-name-NEW";

    Map<String, String> prevCustomMetadata = AI_NOTEBOOK_PREV_PARAMETERS.getMetadata();
    var creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .metadata(prevCustomMetadata);
    var resource = makeNotebookTestResource(workspace.getWorkspaceId(), name, instanceId);
    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledAiNotebookInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveControlledResourceMetadataStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledResourceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build());
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.updateAiNotebookInstance(
                fetchedInstance,
                AI_NOTEBOOK_UPDATE_PARAMETERS,
                newName,
                newDescription,
                user.getAuthenticatedRequest()));

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());
    // cloud notebook attributes are not updated.
    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName(projectId))
            .execute();
    Map<String, String> metadataToUpdate = AI_NOTEBOOK_UPDATE_PARAMETERS.getMetadata();
    Map<String, String> currentCloudInstanceMetadata = instanceFromCloud.getMetadata();
    for (var entrySet : metadataToUpdate.entrySet()) {
      assertEquals(
          prevCustomMetadata.getOrDefault(entrySet.getKey(), ""),
          currentCloudInstanceMetadata.get(entrySet.getKey()));
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateAiNotebookResourceUndo_tryToOverrideTerraReservedMetadataKey()
      throws InterruptedException, IOException {
    String instanceId = "update-ai-notebook-instance-undo-illegal-metadata-key";
    String name = "update-ai-notebook-instance-undo-name-illegal-metadata-key";
    String newName = "update-ai-notebook-instance-undo-name-illegal-metadata-key-NEW";
    String newDescription =
        "new description for update-ai-notebook-instance-undo-name-illegal-metadata-key-NEW";

    var creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspace.getWorkspaceId(), name, instanceId);
    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledAiNotebookInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    var prevInstanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(fetchedInstance.toInstanceName(projectId))
            .execute();

    Map<String, String> illegalMetadataToUpdate = new HashMap<>();
    for (var key : ControlledAiNotebookInstanceResource.RESERVED_METADATA_KEYS) {
      illegalMetadataToUpdate.put(key, RandomStringUtils.random(10));
    }
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            controlledResourceService.updateAiNotebookInstance(
                fetchedInstance,
                new ApiGcpAiNotebookUpdateParameters().metadata(illegalMetadataToUpdate),
                newName,
                newDescription,
                user.getAuthenticatedRequest()));

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());
    // cloud notebook attributes are not updated.
    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName(projectId))
            .execute();
    Map<String, String> currentCloudInstanceMetadata = instanceFromCloud.getMetadata();
    Map<String, String> prevCloudInstanceMetadata = prevInstanceFromCloud.getMetadata();
    for (var entrySet : illegalMetadataToUpdate.entrySet()) {
      assertEquals(
          prevCloudInstanceMetadata.getOrDefault(entrySet.getKey(), ""),
          currentCloudInstanceMetadata.getOrDefault(entrySet.getKey(), ""));
    }
  }

  private ControlledAiNotebookInstanceResource makeNotebookTestResource(
      UUID workspaceUuid, String name, String instanceId) {

    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name(name)
            .assignedUser(user.getEmail())
            .build();

    return ControlledAiNotebookInstanceResource.builder()
        .common(commonFields)
        .instanceId(instanceId)
        .location(DEFAULT_NOTEBOOK_LOCATION)
        .projectId("my-project-id")
        .build();
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
        makeNotebookTestResource(workspace.getWorkspaceId(), instanceId, instanceId);

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
                resource.getWorkspaceId(), resource.getResourceId()));
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
        makeNotebookTestResource(workspace.getWorkspaceId(), instanceId, instanceId);

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
        jobService.retrieveJobResult(createJobId, ControlledAiNotebookInstanceResource.class);
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
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(resource, createdDataset);

    ControlledBigQueryDatasetResource fetchedDataset =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
        fetchedDataset, updateParameters, newName, newDescription);

    ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
                workspace.getWorkspaceId(), resource.getResourceId()));

    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(
        alpha1Service, workspace.getWorkspaceId(), user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createBqDatasetDo() throws Exception {
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
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
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    // Test idempotency of dataset-specific step by retrying it once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
            workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createBqDatasetUndo() throws Exception {
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
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
            controlledResourceService.createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters));

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
                workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteBqDatasetDo() throws Exception {
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
                workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteBqDatasetUndo() throws Exception {
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
                workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetDo() throws Exception {
    // create the dataset
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
    String location = "us-central1";
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
    controlledResourceService.updateBqDataset(resource, updateParameters, newName, newDescription);

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
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(newName, fetchedResource.getName());
    assertEquals(newDescription, fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetUndo() throws Exception {
    // create the dataset
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
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
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
                resource, updateParameters, "NEW_updateBqDatasetUndo", "new resource description"));

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
            .getControlledResource(workspace.getWorkspaceId(), resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(resource.getName(), fetchedResource.getName());
    assertEquals(resource.getDescription(), fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetWithUndefinedExpirationTimes() throws Exception {
    // create the dataset, with expiration times initially defined
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
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
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

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
    controlledResourceService.updateBqDataset(resource, updateParameters, null, null);

    // check the expiration times stored on the cloud are now undefined
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, null, null);

    // update just one expiration time back to a defined value
    Integer newDefaultTableLifetime = 3600;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters().defaultTableLifetime(newDefaultTableLifetime);
    controlledResourceService.updateBqDataset(resource, updateParameters, null, null);

    // check there is one defined and one undefined expiration value
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, newDefaultTableLifetime, null);

    // update the other expiration time back to a defined value
    Integer newDefaultPartitionLifetime = 3601;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    controlledResourceService.updateBqDataset(resource, updateParameters, null, null);

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
    String datasetId = ControlledResourceFixtures.uniqueDatasetId();
    String location = "us-central1";
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspace.getWorkspaceId())
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    // make an update request to set the table expiration time to an invalid value (<3600)
    final ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(3000)
            .defaultPartitionLifetime(3601);
    assertThrows(
        BadRequestException.class,
        () -> controlledResourceService.updateBqDataset(resource, updateParameters, null, null));

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
        () -> controlledResourceService.updateBqDataset(resource, updateParameters2, null, null));

    // check the expiration times stored on the cloud are still undefined, because the update above
    // failed
    validateBigQueryDatasetCloudMetadata(
        projectId, createdDataset.getDatasetName(), location, null, null);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketDo() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
            .build();

    // Test idempotency of bucket-specific steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcsBucketCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertEquals(resource, createdBucket);

    StorageCow storageCow = crlService.createStorageCow(projectId);
    BucketInfo cloudBucket = storageCow.get(resource.getBucketName()).getBucketInfo();
    assertEquals(DEFAULT_REGION, cloudBucket.getLocation().toLowerCase());
    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketDo_invalidBucketName_throwsBadRequestException() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
            .bucketName("192.168.5.4")
            .build();

    assertThrows(
        BadRequestException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketUndo() throws Exception {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
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
            controlledResourceService.createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters()));

    // Validate the bucket does not exist.
    StorageCow storageCow = crlService.createStorageCow(projectId);
    assertNull(storageCow.get(resource.getBucketName()));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspace.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  void cloneGcsBucketDo() throws InterruptedException {
    final ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
            .build();
    final ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertEquals(resource, createdBucket);
    final ApiJobControl apiJobControl = new ApiJobControl().id(UUID.randomUUID().toString());
    final String destinationBucketName =
        "cloned-bucket-" + UUID.randomUUID().toString().toLowerCase();
    final String destinationLocation = "US-EAST1";
    final String jobId =
        controlledResourceService.cloneGcsBucket(
            workspace.getWorkspaceId(),
            createdBucket.getResourceId(),
            workspace.getWorkspaceId(), // copy back into same workspace
            UUID.randomUUID(),
            apiJobControl,
            user.getAuthenticatedRequest(),
            "cloned_bucket",
            "A bucket cloned individually into the same workspace.",
            destinationBucketName,
            destinationLocation,
            ApiCloningInstructionsEnum.RESOURCE);

    jobService.waitForJob(jobId);
    final FlightState flightState = stairwayComponent.get().getFlightState(jobId);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(flightState.getException().isEmpty());
    assertTrue(flightState.getResultMap().isPresent());
    var response =
        flightState
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpGcsBucket.class);
    assertNotNull(response);
    assertEquals(
        destinationBucketName, response.getBucket().getGcpBucket().getAttributes().getBucketName());
    final UUID resourceId = response.getBucket().getResourceId();
    final ControlledResource destinationControlledResource =
        controlledResourceService.getControlledResource(workspace.getWorkspaceId(), resourceId);
    final ControlledGcsBucketResource destinationBucketResource =
        destinationControlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertNotNull(destinationBucketResource);
    assertEquals("cloned_bucket", destinationBucketResource.getName());

    // check creation parameters on cloud (not stored by WSM). Source project is same as destination
    // in this case.
    final StorageCow storageCow =
        crlService.createStorageCow(
            gcpCloudContextService.getRequiredGcpProject(workspace.getWorkspaceId()));
    final BucketCow bucketCow = storageCow.get(destinationBucketName);
    final BucketInfo bucketInfo = bucketCow.getBucketInfo();
    assertEquals(destinationLocation, bucketInfo.getLocation());
    assertEquals(
        GcsApiConversions.toGcsApi(
            ControlledResourceFixtures.getGoogleBucketCreationParameters()
                .getDefaultStorageClass()),
        bucketInfo.getStorageClass());
    final List<LifecycleRule> expectedLifecycleRules =
        GcsApiConversions.toGcsApi(
            ControlledResourceFixtures.getGoogleBucketCreationParameters()
                .getLifecycle()
                .getRules());
    assertThat(
        expectedLifecycleRules, containsInAnyOrder(bucketInfo.getLifecycleRules().toArray()));
  }

  @Test
  void cloneGcsBucketTwice_lineageAppends() throws InterruptedException {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
            .build();
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    // original bucket
    ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    var destinationLocation = "US-EAST1";
    // clone bucket once
    String jobId =
        controlledResourceService.cloneGcsBucket(
            workspace.getWorkspaceId(),
            createdBucket.getResourceId(),
            workspace.getWorkspaceId(), // copy back into same workspace
            UUID.randomUUID(),
            new ApiJobControl().id(UUID.randomUUID().toString()),
            user.getAuthenticatedRequest(),
            "first_cloned_bucket",
            "A bucket cloned individually into the same workspace.",
            "cloned-bucket-" + UUID.randomUUID().toString().toLowerCase(),
            destinationLocation,
            ApiCloningInstructionsEnum.RESOURCE);

    jobService.waitForJob(jobId);
    FlightState flightState = stairwayComponent.get().getFlightState(jobId);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    var response =
        flightState
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpGcsBucket.class);
    UUID firstClonedBucketResourceId = response.getBucket().getResourceId();
    ControlledGcsBucketResource firstClonedBucket =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), firstClonedBucketResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    expectedLineage.add(
        new ResourceLineageEntry(workspace.getWorkspaceId(), createdBucket.getResourceId()));
    assertEquals(expectedLineage, firstClonedBucket.getResourceLineage());

    // clone twice.
    String jobId2 =
        controlledResourceService.cloneGcsBucket(
            workspace.getWorkspaceId(),
            firstClonedBucketResourceId,
            workspace.getWorkspaceId(), // copy back into same workspace
            UUID.randomUUID(),
            new ApiJobControl().id(UUID.randomUUID().toString()),
            user.getAuthenticatedRequest(),
            "second_cloned_bucket",
            "A bucket cloned individually into the same workspace.",
            "second-cloned-bucket-" + UUID.randomUUID().toString().toLowerCase(),
            destinationLocation,
            ApiCloningInstructionsEnum.RESOURCE);

    jobService.waitForJob(jobId2);
    FlightState flightState2 = stairwayComponent.get().getFlightState(jobId2);
    assertEquals(FlightStatus.SUCCESS, flightState2.getFlightStatus());
    var response2 =
        flightState2
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpGcsBucket.class);
    UUID secondCloneResourceId = response2.getBucket().getResourceId();
    ControlledGcsBucketResource secondClonedBucket =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), secondCloneResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    expectedLineage.add(
        new ResourceLineageEntry(workspace.getWorkspaceId(), firstClonedBucketResourceId));
    assertEquals(expectedLineage, secondClonedBucket.getResourceLineage());
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
                workspace.getWorkspaceId(), createdBucket.getResourceId()));
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
        newName,
        newDescription,
        user.getAuthenticatedRequest());

    // check the properties stored in WSM were updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), createdBucket.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

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
                newName,
                newDescription,
                user.getAuthenticatedRequest()));

    // check the properties stored on the cloud were not updated
    BucketInfo updatedBucket =
        crlService.createStorageCow(projectId).get(createdBucket.getBucketName()).getBucketInfo();
    ApiGcpGcsBucketUpdateParameters cloudParameters =
        GcsApiConversions.toUpdateParameters(updatedBucket);
    assertNotEquals(ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_2, cloudParameters);

    // check the properties stored in WSM were not updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), createdBucket.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
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
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspace.getWorkspaceId())
            .build();

    ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                originalResource,
                null,
                user.getAuthenticatedRequest(),
                ControlledResourceFixtures.getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
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
