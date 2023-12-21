package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.AI_NOTEBOOK_PREV_PARAMETERS;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.AI_NOTEBOOK_UPDATE_PARAMETERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.UpdateInstanceResourceLocationAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.NotebookCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.RetrieveAiNotebookResourceAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.UpdateAiNotebookAttributesStep;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceRegionStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.flight.UpdateFinishStep;
import bio.terra.workspace.service.resource.flight.UpdateStartStep;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import com.google.api.services.notebooks.v1.model.Instance;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledResourceServiceNotebookTest extends BaseConnectedTest {
  /** The default roles to use when creating user private AI notebook instance resources */
  private static final ControlledResourceIamRole DEFAULT_ROLE = ControlledResourceIamRole.WRITER;

  /** The default GCP location to create notebooks for this test. */
  private static final String DEFAULT_NOTEBOOK_LOCATION = "us-east1-b";

  // Store workspaceId instead of workspace so that for local development, one can easily use a
  // previously created workspace.
  private UUID workspaceId;
  private UserAccessUtils.TestUser user;
  private String projectId;

  @Autowired private CliConfiguration cliConfiguration;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private PetSaService petSaService;
  @Autowired private SamService samService;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private WsmResourceService wsmResourceService;

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

  @BeforeAll
  public void setup() {
    user = userAccessUtils.defaultUser();
    workspaceId =
        workspaceUtils
            .createWorkspaceWithGcpContext(user.getAuthenticatedRequest())
            .getWorkspaceId();
    projectId = gcpCloudContextService.getRequiredReadyGcpProject(workspaceId);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId, user.getAuthenticatedRequest());
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  public void cleanUp() {
    user = userAccessUtils.defaultUser();
    Workspace workspace = workspaceService.getWorkspace(workspaceId);
    workspaceService.deleteWorkspace(workspace, user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceDo() throws Exception {
    String workspaceUserFacingId = workspaceService.getWorkspace(workspaceId).getUserFacingId();
    var instanceId = "create-ai-notebook-instance-do";
    var serverName = "verily-autopush";

    cliConfiguration.setServerName(serverName);
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);

    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspaceId, "initial-notebook-name", instanceId);

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveNetworkNameStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(NotebookCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateInstanceResourceLocationAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
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

    assertTrue(
        resource.partialEqual(
            controlledResourceService.getControlledResource(
                workspaceId, resource.getResourceId())));

    InstanceName instanceName = resource.toInstanceName();
    Instance instance =
        crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();

    // NOTE: permission checks proved unreliable, so have been removed

    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-cli-server", serverName));
    assertThat(
        instance.getMetadata(), Matchers.hasEntry("terra-workspace-id", workspaceUserFacingId));

    // Creating a controlled resource with a duplicate underlying notebook instance is not allowed.
    ControlledAiNotebookInstanceResource duplicateResource =
        makeNotebookTestResource(workspaceId, "new-name-same-notebook-instance", instanceId);
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
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspaceId, name, instanceId);

    // Test idempotency of undo steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    // The finish step is not undoable, so we make the failure at the penultimate step.
    Map<String, StepStatus> triggerFailureStep = new HashMap<>();
    triggerFailureStep.put(
        UpdateControlledResourceRegionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder()
            .doStepFailures(triggerFailureStep)
            .undoStepFailures(retrySteps)
            .build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    // Revoke user's Pet SA access, if they have it. Because these tests re-use a common workspace,
    // the user may have pet SA access enabled prior to this test.
    String serviceAccountEmail =
        samService.getOrCreatePetSaEmail(
            projectId, user.getAuthenticatedRequest().getRequiredToken());
    petSaService.disablePetServiceAccountImpersonation(
        workspaceId, user.getEmail(), user.getAuthenticatedRequest());
    IamCow userIamCow = crlService.getIamCow(user.getAuthenticatedRequest());
    // Assert the user does not have access to their pet SA before the flight
    // Note this uses user credentials for the IAM cow to validate the user's access.
    GcpCloudUtils.runWithRetryOnException(
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

    assertNotFound(resource.toInstanceName(), crlService.getAIPlatformNotebooksCow());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(), resource.getResourceId()));
    // This check relies on cloud IAM propagation and is sometimes delayed.
    GcpCloudUtils.runWithRetryOnException(
        () ->
            throwIfImpersonateSa(
                ServiceAccountName.builder()
                    .projectId(projectId)
                    .email(serviceAccountEmail)
                    .build(),
                userIamCow));
    // Run and undo the flight again, this time triggered by the default user's pet SA instead of
    // the default user themselves. This should behave the same as the flight triggered by the
    // end-user credentials but has historically hidden bugs, so is worth testing explicitly.
    AuthenticatedUserRequest petCredentials =
        petSaService.getWorkspacePetCredentials(workspaceId, user.getAuthenticatedRequest()).get();
    String petJobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            DEFAULT_ROLE,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            petCredentials);
    jobService.waitForJob(petJobId);
    // Confirm this flight status is ERROR, not FATAL.
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(petJobId).getFlightStatus());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateAiNotebookResourceDo() throws InterruptedException, IOException {
    var instanceId = "update-ai-notebook-instance-do";
    var name = "update-ai-notebook-instance-do-name";
    var newName = "update-ai-notebook-instance-do-name-NEW";
    var newDescription = "new description for update-ai-notebook-instance-do-name-NEW";

    var creationParameters =
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspaceId, name, instanceId);
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
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(fetchedInstance.toInstanceName())
            .execute();
    var metadata = instanceFromCloud.getMetadata();

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    wsmResourceService.updateResource(
        user.getAuthenticatedRequest(),
        fetchedInstance,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        AI_NOTEBOOK_UPDATE_PARAMETERS);

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(newName, updatedInstance.getName());
    assertEquals(newDescription, updatedInstance.getDescription());
    // cloud notebook attributes are updated.
    var updatedInstanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName())
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
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspaceId, name, instanceId);
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
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    crlService
        .getAIPlatformNotebooksCow()
        .instances()
        .get(fetchedInstance.toInstanceName())
        .execute();

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    wsmResourceService.updateResource(
        user.getAuthenticatedRequest(),
        fetchedInstance,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        null);

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
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
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION)
            .metadata(prevCustomMetadata);
    var resource = makeNotebookTestResource(workspaceId, name, instanceId);
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
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    Map<String, StepStatus> doErrorStep = new HashMap<>();
    doErrorStep.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveAiNotebookResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateAiNotebookAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(doErrorStep)
            .undoStepFailures(retrySteps)
            .build());
    assertThrows(
        InvalidResultStateException.class,
        () ->
            wsmResourceService.updateResource(
                user.getAuthenticatedRequest(),
                fetchedInstance,
                new CommonUpdateParameters().setName(newName).setDescription(newDescription),
                AI_NOTEBOOK_UPDATE_PARAMETERS));

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());
    // cloud notebook attributes are not updated.
    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName())
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
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    var resource = makeNotebookTestResource(workspaceId, name, instanceId);
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
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    var prevInstanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(fetchedInstance.toInstanceName())
            .execute();

    Map<String, String> illegalMetadataToUpdate = new HashMap<>();
    for (var key : ControlledAiNotebookInstanceResource.RESERVED_METADATA_KEYS) {
      illegalMetadataToUpdate.put(key, RandomStringUtils.random(10));
    }
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            wsmResourceService.updateResource(
                user.getAuthenticatedRequest(),
                fetchedInstance,
                new CommonUpdateParameters().setName(newName).setDescription(newDescription),
                new ApiGcpAiNotebookUpdateParameters().metadata(illegalMetadataToUpdate)));

    ControlledAiNotebookInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());
    // cloud notebook attributes are not updated.
    var instanceFromCloud =
        crlService
            .getAIPlatformNotebooksCow()
            .instances()
            .get(updatedInstance.toInstanceName())
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
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name(name)
            .assignedUser(user.getEmail())
            .build();

    return ControlledAiNotebookInstanceResource.builder()
        .common(commonFields)
        .instanceId(instanceId)
        .location(DEFAULT_NOTEBOOK_LOCATION)
        .projectId(projectId)
        .build();
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createAiNotebookInstanceNoWriterRoleThrowsBadRequest() {
    String instanceId = "create-ai-notebook-instance-shared";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspaceId, instanceId, instanceId);

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
  void deleteAiNotebookInstanceDo() {
    ControlledAiNotebookInstanceResource resource =
        createDefaultPrivateAiNotebookInstance("delete-ai-notebook-instance-do", user);
    InstanceName instanceName = resource.toInstanceName();

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteAiNotebookInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), false, user.getAuthenticatedRequest());
    assertNotFound(instanceName, notebooks);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteAiNotebookInstanceUndoIsDismalFailure() {
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
                false,
                user.getAuthenticatedRequest()));
  }

  /** Create a controlled AI Notebook instance with default private settings. */
  private ControlledAiNotebookInstanceResource createDefaultPrivateAiNotebookInstance(
      String instanceId, UserAccessUtils.TestUser user) {
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(DEFAULT_NOTEBOOK_LOCATION);
    ControlledAiNotebookInstanceResource resource =
        makeNotebookTestResource(workspaceId, instanceId, instanceId);

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
}
