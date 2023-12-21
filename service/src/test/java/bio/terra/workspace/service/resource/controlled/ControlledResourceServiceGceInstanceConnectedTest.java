package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.GCE_INSTANCE_PREV_PARAMETERS;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.GCE_INSTANCE_UPDATE_PARAMETERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
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
import bio.terra.workspace.generated.model.ApiGcpGceInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceGuestAccelerator;
import bio.terra.workspace.generated.model.ApiGcpGceUpdateParameters;
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
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.CreateGceInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.DeleteGceInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.GceInstanceCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.RetrieveGceInstanceResourceAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.UpdateGceInstanceAttributesStep;
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
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class ControlledResourceServiceGceInstanceConnectedTest extends BaseConnectedTest {
  /** The default roles to use when creating user private GCE instance resources */
  private static final ControlledResourceIamRole DEFAULT_ROLE = ControlledResourceIamRole.WRITER;

  /** The default GCP location to create instancess for this test. */
  private static final String DEFAULT_INSTANCE_ZONE = "us-east1-b";

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

  private static void assertNotFound(
      String projectId, String zone, String instanceId, CloudComputeCow cloudComputeCow) {
    GoogleJsonResponseException exception =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> cloudComputeCow.instances().get(projectId, zone, instanceId).execute());
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
  void createGceInstanceDo() throws Exception {
    String workspaceUserFacingId = workspaceService.getWorkspace(workspaceId).getUserFacingId();
    String instanceId = "create-gce-instance-do";
    String serverName = "verily-autopush";

    cliConfiguration.setServerName(serverName);
    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE)
            .addGuestAcceleratorsItem(
                new ApiGcpGceInstanceGuestAccelerator().cardCount(1).type("nvidia-tesla-p100"));

    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, "initial-instance-name", instanceId);

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveNetworkNameStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateGceInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GceInstanceCloudSyncStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateInstanceResourceLocationAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        controlledResourceService.createGceInstance(
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

    Instance instance =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
            .execute();

    // NOTE: permission checks proved unreliable, so have been removed

    Map<String, String> metadata =
        instance.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    assertThat(metadata, Matchers.hasEntry("terra-cli-server", serverName));
    assertThat(metadata, Matchers.hasEntry("terra-workspace-id", workspaceUserFacingId));

    // Creating a controlled resource with a duplicate underlying instance is not allowed.
    ControlledGceInstanceResource duplicateResource =
        makeInstanceTestResource(workspaceId, "new-name-same-instance", instanceId);
    String duplicateResourceJobId =
        controlledResourceService.createGceInstance(
            duplicateResource,
            creationParameters,
            DEFAULT_ROLE,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());

    jobService.waitForJob(duplicateResourceJobId);
    JobService.JobResultOrException<ControlledGceInstanceResource> duplicateJobResult =
        jobService.retrieveJobResult(duplicateResourceJobId, ControlledGceInstanceResource.class);
    assertEquals(DuplicateResourceException.class, duplicateJobResult.getException().getClass());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGceInstanceUndo() throws Exception {
    String instanceId = "create-gce-instance-undo";
    String name = "create-gce-instance-undo-name";

    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, name, instanceId);

    // Test idempotency of undo steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GrantPetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateGceInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
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
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            DEFAULT_ROLE,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    assertNotFound(
        resource.getProjectId(),
        resource.getZone(),
        resource.getInstanceId(),
        crlService.getCloudComputeCow());
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
        controlledResourceService.createGceInstance(
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
  void updateGceInstanceResourceDo() throws InterruptedException, IOException {
    String instanceId = "update-gce-instance-do";
    String name = "update-gce-instance-do-name";
    String newName = "update-gce-instance-do-name-NEW";
    String newDescription = "new description for update-gce-instance-do-name-NEW";

    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, name, instanceId);
    String jobId =
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledGceInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);

    Instance instanceFromCloud =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
            .execute();
    Map<String, String> metadata =
        instanceFromCloud.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGceInstanceResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateGceInstanceAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    wsmResourceService.updateResource(
        user.getAuthenticatedRequest(),
        fetchedInstance,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        GCE_INSTANCE_UPDATE_PARAMETERS);

    ControlledGceInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    // resource metadata is updated.
    assertEquals(newName, updatedInstance.getName());
    assertEquals(newDescription, updatedInstance.getDescription());

    // cloud instance attributes are updated.
    Instance updatedInstanceFromCloud =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
            .execute();
    // Merge metadata from GCE_INSTANCE_UPDATE_PARAMETERS to metadata.
    GCE_INSTANCE_UPDATE_PARAMETERS
        .getMetadata()
        .forEach(
            (key, value) ->
                metadata.merge(
                    key, value, (v1, v2) -> v1.equalsIgnoreCase(v2) ? v1 : v1 + "," + v2));
    Map<String, String> newMetadata =
        updatedInstanceFromCloud.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    for (Entry<String, String> entrySet : GCE_INSTANCE_UPDATE_PARAMETERS.getMetadata().entrySet()) {
      assertEquals(entrySet.getValue(), newMetadata.get(entrySet.getKey()));
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGceInstanceResourceDo_nameAndDescriptionOnly()
      throws InterruptedException, IOException {
    String instanceId = "update-gce-instance-do-name-and-description-only";
    String name = "update-gce-instance-do-name-and-description-only";
    String newName = "update-gce-instance-do-name-and-description-only-NEW";
    String newDescription =
        "new description for update-gce-instance-do-name-and-description-only-NEW";

    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, name, instanceId);
    String jobId =
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledGceInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGceInstanceResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateGceInstanceAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    wsmResourceService.updateResource(
        user.getAuthenticatedRequest(),
        fetchedInstance,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        null);

    ControlledGceInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    // resource metadata is updated.
    assertEquals(newName, updatedInstance.getName());
    assertEquals(newDescription, updatedInstance.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGceInstanceResourceUndo() throws InterruptedException, IOException {
    String instanceId = "update-gce-instance-undo";
    String name = "update-gce-instance-undo-name";
    String newName = "update-gce-instance-undo-name-NEW";
    String newDescription = "new description for update-gce-instance-undo-name-NEW";

    Map<String, String> prevCustomMetadata = GCE_INSTANCE_PREV_PARAMETERS.getMetadata();
    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE)
            .metadata(prevCustomMetadata);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, name, instanceId);
    String jobId =
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledGceInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);

    Map<String, StepStatus> doErrorStep = new HashMap<>();
    doErrorStep.put(
        UpdateGceInstanceAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveGceInstanceResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateGceInstanceAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

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
                GCE_INSTANCE_UPDATE_PARAMETERS));

    ControlledGceInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());

    // cloud attributes are not updated.
    Instance instanceFromCloud =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(
                updatedInstance.getProjectId(),
                updatedInstance.getZone(),
                updatedInstance.getInstanceId())
            .execute();
    Map<String, String> metadataToUpdate = GCE_INSTANCE_UPDATE_PARAMETERS.getMetadata();
    Map<String, String> currentCloudInstanceMetadata =
        instanceFromCloud.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    for (Entry<String, String> entrySet : metadataToUpdate.entrySet()) {
      assertEquals(
          prevCustomMetadata.getOrDefault(entrySet.getKey(), ""),
          currentCloudInstanceMetadata.get(entrySet.getKey()));
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void updateGceInstanceResourceUndo_tryToOverrideTerraReservedMetadataKey()
      throws InterruptedException, IOException {
    String instanceId = "update-gce-instance-undo-illegal-metadata-key";
    String name = "update-gce-instance-undo-name-illegal-metadata-key";
    String newName = "update-gce-instance-undo-name-illegal-metadata-key-NEW";
    String newDescription =
        "new description for update-gce-instance-undo-name-illegal-metadata-key-NEW";

    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, name, instanceId);
    String jobId =
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            ControlledResourceIamRole.EDITOR,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fakeResultPath",
            user.getAuthenticatedRequest());
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    ControlledGceInstanceResource fetchedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    Instance prevInstanceFromCloud =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(
                fetchedInstance.getProjectId(),
                fetchedInstance.getZone(),
                fetchedInstance.getInstanceId())
            .execute();

    Map<String, String> illegalMetadataToUpdate = new HashMap<>();
    for (String key : ControlledGceInstanceResource.RESERVED_METADATA_KEYS) {
      illegalMetadataToUpdate.put(key, RandomStringUtils.random(10));
    }
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            wsmResourceService.updateResource(
                user.getAuthenticatedRequest(),
                fetchedInstance,
                new CommonUpdateParameters().setName(newName).setDescription(newDescription),
                new ApiGcpGceUpdateParameters().metadata(illegalMetadataToUpdate)));

    ControlledGceInstanceResource updatedInstance =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    // resource metadata is updated.
    assertEquals(resource.getName(), updatedInstance.getName());
    assertEquals(resource.getDescription(), updatedInstance.getDescription());

    // cloud attributes are not updated.
    Instance instanceFromCloud =
        crlService
            .getCloudComputeCow()
            .instances()
            .get(
                updatedInstance.getProjectId(),
                updatedInstance.getZone(),
                updatedInstance.getInstanceId())
            .execute();
    Map<String, String> currentCloudInstanceMetadata =
        instanceFromCloud.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    Map<String, String> prevCloudInstanceMetadata =
        prevInstanceFromCloud.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    for (Entry<String, String> entrySet : illegalMetadataToUpdate.entrySet()) {
      assertEquals(
          prevCloudInstanceMetadata.getOrDefault(entrySet.getKey(), ""),
          currentCloudInstanceMetadata.getOrDefault(entrySet.getKey(), ""));
    }
  }

  private ControlledGceInstanceResource makeInstanceTestResource(
      UUID workspaceUuid, String name, String instanceId) {

    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeGceInstanceCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name(name)
            .assignedUser(user.getEmail())
            .build();

    return ControlledGceInstanceResource.builder()
        .common(commonFields)
        .instanceId(instanceId)
        .zone(DEFAULT_INSTANCE_ZONE)
        .projectId(projectId)
        .build();
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGceInstanceNoWriterRoleThrowsBadRequest() throws Exception {
    String instanceId = "create-gce-instance-shared";

    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, instanceId, instanceId);

    // Shared instances not yet implemented.
    // Private IAM roles must include writer role.
    ControlledResourceIamRole notWriter = ControlledResourceIamRole.READER;
    BadRequestException noWriterException =
        assertThrows(
            BadRequestException.class,
            () ->
                controlledResourceService.createGceInstance(
                    resource,
                    creationParameters,
                    notWriter,
                    new ApiJobControl().id(UUID.randomUUID().toString()),
                    "fakeResultPath",
                    user.getAuthenticatedRequest()));
    assertEquals(
        "A private, controlled compute instance must have the writer or editor role or else it is not useful.",
        noWriterException.getMessage());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteGceInstanceDo() throws Exception {
    ControlledGceInstanceResource resource =
        createDefaultPrivateGceInstance("delete-gce-instance-do", user);

    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteGceInstanceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), false, user.getAuthenticatedRequest());
    assertNotFound(
        resource.getProjectId(), resource.getZone(), resource.getInstanceId(), cloudComputeCow);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                resource.getWorkspaceId(), resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteGceInstanceUndoIsDismalFailure() throws Exception {
    ControlledGceInstanceResource resource =
        createDefaultPrivateGceInstance("delete-gce-instance-undo", user);

    // Test that trying to undo a instance deletion is a dismal failure. We cannot undo deletion.
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

  /** Create a controlled AI GCE instance with default private settings. */
  private ControlledGceInstanceResource createDefaultPrivateGceInstance(
      String instanceId, UserAccessUtils.TestUser user) {
    ApiGcpGceInstanceCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
            .instanceId(instanceId)
            .zone(DEFAULT_INSTANCE_ZONE);
    ControlledGceInstanceResource resource =
        makeInstanceTestResource(workspaceId, instanceId, instanceId);

    String createJobId =
        controlledResourceService.createGceInstance(
            resource,
            creationParameters,
            DEFAULT_ROLE,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            null,
            user.getAuthenticatedRequest());
    jobService.waitForJob(createJobId);
    JobService.JobResultOrException<ControlledGceInstanceResource> creationResult =
        jobService.retrieveJobResult(createJobId, ControlledGceInstanceResource.class);
    assertNull(creationResult.getException(), "Error creating controlled GCE instance");
    assertNotNull(creationResult.getResult(), "Unexpected null created controlled GCE instance");
    return creationResult.getResult();
  }
}
