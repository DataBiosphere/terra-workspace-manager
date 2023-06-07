package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BUCKET_UPDATE_PARAMETERS_2;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.getGoogleBucketCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.CreateGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.DeleteGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsBucketCloudSyncStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.UpdateGcsBucketStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.flight.UpdateFinishStep;
import bio.terra.workspace.service.resource.flight.UpdateStartStep;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.cloud.storage.BucketInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledResourceServiceBucketTest extends BaseConnectedTest {

  // Store workspaceId instead of workspace so that for local development, one can easily use a
  // previously created workspace.
  private UUID workspaceId;
  private UserAccessUtils.TestUser user;
  private String projectId;

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired private WsmResourceService wsmResourceService;

  @BeforeAll
  public void setup() {
    user = userAccessUtils.defaultUser();
    workspaceId =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
    projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
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
  void createGcsBucketDo_invalidBucketName_throwsBadRequestException() {
    ControlledGcsBucketResource resource =
        makeDefaultControlledGcsBucketBuilder(workspaceId).bucketName("192.168.5.4").build();

    assertThrows(
        BadRequestException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                resource,
                null,
                user.getAuthenticatedRequest(),
                getGoogleBucketCreationParameters()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGcsBucketUndo() {
    ControlledGcsBucketResource resource =
        makeDefaultControlledGcsBucketBuilder(workspaceId).build();

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
                getGoogleBucketCreationParameters()));

    // Validate the bucket does not exist.
    StorageCow storageCow = crlService.createStorageCow(projectId);
    assertNull(storageCow.get(resource.getBucketName()));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(workspaceId, resource.getResourceId()));
  }

  @Test
  void cloneGcsBucketTwice_lineageAppends() throws InterruptedException {
    ControlledGcsBucketResource resource =
        makeDefaultControlledGcsBucketBuilder(workspaceId).build();
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    // original bucket
    ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    var destinationLocation = "US-EAST1";
    // clone bucket once
    String jobId =
        controlledResourceService.cloneGcsBucket(
            workspaceId,
            createdBucket.getResourceId(),
            workspaceId, // copy back into same workspace
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
            .getControlledResource(workspaceId, firstClonedBucketResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    expectedLineage.add(new ResourceLineageEntry(workspaceId, createdBucket.getResourceId()));
    assertEquals(expectedLineage, firstClonedBucket.getResourceLineage());

    // clone twice.
    String jobId2 =
        controlledResourceService.cloneGcsBucket(
            workspaceId,
            firstClonedBucketResourceId,
            workspaceId, // copy back into same workspace
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
            .getControlledResource(workspaceId, secondCloneResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    expectedLineage.add(new ResourceLineageEntry(workspaceId, firstClonedBucketResourceId));
    assertEquals(expectedLineage, secondClonedBucket.getResourceLineage());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteGcsBucketDo() throws Exception {
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(user);

    // Test idempotency of bucket-specific delete step by retrying it once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            new ApiJobControl().id(UUID.randomUUID().toString()),
            workspaceId,
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
                workspaceId, createdBucket.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGcsBucketDo() {
    Workspace workspace = workspaceService.getWorkspace(workspaceId);
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(user);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcsBucketCloudAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    // update the bucket
    var commonUpdateParameters =
        new CommonUpdateParameters()
            .setName("NEW_bucketname")
            .setDescription("new resource description");

    wsmResourceService.updateResource(
        user.getAuthenticatedRequest(),
        createdBucket,
        commonUpdateParameters,
        BUCKET_UPDATE_PARAMETERS_2);

    // check the properties stored in WSM were updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceId, createdBucket.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    assertEquals(commonUpdateParameters.getName(), fetchedResource.getName());
    assertEquals(commonUpdateParameters.getDescription(), fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateGcsBucketUndo() {
    ControlledGcsBucketResource createdBucket = createDefaultSharedGcsBucket(user);

    Map<String, StepStatus> doErrorStep = new HashMap<>();
    doErrorStep.put(UpdateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RetrieveGcsBucketCloudAttributesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(UpdateGcsBucketStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(doErrorStep)
            .undoStepFailures(retrySteps)
            .build());

    // update the bucket
    var commonUpdateParameters =
        new CommonUpdateParameters()
            .setName("NEW_bucketname")
            .setDescription("new resource description");

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            wsmResourceService.updateResource(
                user.getAuthenticatedRequest(),
                createdBucket,
                commonUpdateParameters,
                BUCKET_UPDATE_PARAMETERS_2));

    // check the properties stored on the cloud were not updated
    BucketInfo updatedBucket =
        crlService.createStorageCow(projectId).get(createdBucket.getBucketName()).getBucketInfo();
    ApiGcpGcsBucketUpdateParameters cloudParameters =
        GcsApiConversions.toUpdateParameters(updatedBucket);
    assertNotEquals(BUCKET_UPDATE_PARAMETERS_2, cloudParameters);

    // check the properties stored in WSM were not updated
    ControlledGcsBucketResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceId, createdBucket.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertEquals(createdBucket.getName(), fetchedResource.getName());
    assertEquals(createdBucket.getDescription(), fetchedResource.getDescription());
  }

  /**
   * Creates a user-shared controlled GCS bucket in the provided workspace, using the credentials of
   * the provided user. This uses the default bucket creation parameters from {@code
   * ControlledResourceFixtures}.
   */
  private ControlledGcsBucketResource createDefaultSharedGcsBucket(UserAccessUtils.TestUser user) {
    ControlledGcsBucketResource originalResource =
        makeDefaultControlledGcsBucketBuilder(workspaceId).build();

    ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                originalResource,
                null,
                user.getAuthenticatedRequest(),
                getGoogleBucketCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertTrue(originalResource.partialEqual(createdBucket));
    return createdBucket;
  }
}
