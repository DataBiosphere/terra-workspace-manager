package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_REGION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Tag("aws-connected")
@TestInstance(Lifecycle.PER_CLASS)
public class AwsS3StorageFolderFlightTest extends BaseAwsConnectedTest {
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private JobService jobService;
  @Autowired private StairwayComponent stairwayComponent;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired UserAccessUtils userAccessUtils;

  private AuthenticatedUserRequest userRequest;
  private UUID workspaceUuid;
  private LandingZone landingZone;
  private AwsCredentialsProvider awsCredentialsProvider;

  @BeforeAll
  public void init() throws Exception {
    super.init();
    userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    workspaceUuid =
        mockWorkspaceV2Api.createWorkspaceAndWait(userRequest, apiCloudPlatform).getWorkspaceId();
    Environment environment = awsCloudContextService.discoverEnvironment(userRequest.getEmail());
    landingZone =
        AwsCloudContextService.getLandingZone(
                environment,
                awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid),
                Region.of(AWS_REGION))
            .orElseThrow();
    awsCredentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(), environment);
  }

  @AfterAll
  public void cleanUp() throws Exception {
    mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceUuid);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceUuid, userRequest);
  }

  @Test
  void createGetUpdateDeleteS3StorageFolderTest() throws InterruptedException {
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());
    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResource(
            workspaceUuid, landingZone.getStorageBucket().name(), creationParameters);

    // create resource
    ControlledAwsS3StorageFolderResource createdResource =
        controlledResourceService
            .createControlledResourceSync(resource, null, userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    assertTrue(resource.partialEqual(createdResource));

    // verify resource created
    assertTrue(AwsUtils.checkFolderExists(awsCredentialsProvider, resource));
    ControlledAwsS3StorageFolderResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, createdResource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    assertEquals(resource.getBucketName(), fetchedResource.getBucketName());
    assertEquals(resource.getPrefix(), fetchedResource.getPrefix());

    // update resource
    CommonUpdateParameters updateParameters =
        new CommonUpdateParameters().setName("updatedName").setDescription("updated description");
    wsmResourceService.updateResource(userRequest, createdResource, updateParameters, null);

    // verify resource updated
    ControlledAwsS3StorageFolderResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, createdResource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    assertEquals(updateParameters.getName(), updatedResource.getName());
    assertEquals(updateParameters.getDescription(), updatedResource.getDescription());

    // delete resource
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            UUID.randomUUID().toString(),
            workspaceUuid,
            createdResource.getResourceId(),
            /* forceDelete= */ false,
            "delete-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // verify resource deleted
    assertFalse(AwsUtils.checkFolderExists(awsCredentialsProvider, resource));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspaceUuid, resource.getResourceId()));
  }

  @Test
  void createS3StorageFolder_undo_deletedTest() {
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());
    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResource(
            workspaceUuid, landingZone.getStorageBucket().name(), creationParameters);

    // test idempotency of s3-folder-specific undo step by retrying once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CreateAwsS3StorageFolderStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    // final createResponseStep cannot fail, hence fail the create step
    Map<String, StepStatus> failureSteps = new HashMap<>();
    failureSteps.put(
        CreateAwsS3StorageFolderStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(failureSteps)
            .undoStepFailures(retrySteps)
            .build());
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                resource, null, userRequest, creationParameters));

    // validate resource does not exist
    assertFalse(AwsUtils.checkFolderExists(awsCredentialsProvider, resource));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspaceUuid, resource.getResourceId()));
  }

  @Test
  void deleteS3StorageFolder_undo_stillDeletedTest() throws InterruptedException {
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());
    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResource(
            workspaceUuid, landingZone.getStorageBucket().name(), creationParameters);

    ControlledAwsS3StorageFolderResource createdResource =
        controlledResourceService
            .createControlledResourceSync(resource, null, userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    // None of the steps on this flight are undoable, so even with lastStepFailure set to true we
    // should expect the resource to really be deleted.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());

    // delete resource
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            UUID.randomUUID().toString(),
            workspaceUuid,
            createdResource.getResourceId(),
            /* forceDelete= */ false,
            "delete-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.FATAL, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // validate resource does not exist.
    assertFalse(AwsUtils.checkFolderExists(awsCredentialsProvider, resource));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspaceUuid, resource.getResourceId()));
  }
}
