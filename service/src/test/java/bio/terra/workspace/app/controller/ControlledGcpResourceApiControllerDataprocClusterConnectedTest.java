package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.mocks.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.mocks.MockMvcUtils.assertResourceMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for controlled Dataproc clusters. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.

@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerDataprocClusterConnectedTest
    extends BaseConnectedTest {
  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired MockGcpApi mockGcpApi;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;

  private UUID workspaceId;
  private UUID stagingBucketUuid;
  private UUID tempBucketUuid;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockWorkspaceV1Api
            .createWorkspaceWithCloudContext(
                userAccessUtils.defaultUserAuthRequest(), apiCloudPlatform)
            .getId();

    // Create staging and temp controlled resource buckets
    String stagingBucketResourceName = TestUtils.appendRandomNumber("dataproc-staging-bucket");
    String stagingBucketCloudName = TestUtils.appendRandomNumber("dataproc-staging-bucket");
    ApiGcpGcsBucketResource stagingBucketResource =
        mockGcpApi
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                stagingBucketResourceName,
                stagingBucketCloudName,
                /*location*/ null,
                /*storageClass*/ null,
                /*lifecycle*/ null)
            .getGcpBucket();

    String tempBucketResourceName = TestUtils.appendRandomNumber("dataproc-temp-bucket");
    String tempBucketCloudName = TestUtils.appendRandomNumber("dataproc-temp-bucket");
    ApiGcpGcsBucketResource tempBucketResource =
        mockGcpApi
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                tempBucketResourceName,
                tempBucketCloudName,
                /*location*/ null,
                /*storageClass*/ null,
                /*lifecycle*/ null)
            .getGcpBucket();

    stagingBucketUuid = stagingBucketResource.getMetadata().getResourceId();
    tempBucketUuid = tempBucketResource.getMetadata().getResourceId();
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId, userAccessUtils.defaultUserAuthRequest());
    jobService.setFlightDebugInfoForTest(null);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockWorkspaceV2Api.deleteWorkspaceAndWait(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void createDataprocCluster() throws Exception {
    mockWorkspaceV1Api.updateWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(
            new ApiProperty()
                .key(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION)
                .value("asia-east1")));

    ApiGcpDataprocClusterResource cluster =
        mockGcpApi
            .createDataprocCluster(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                "asia-east1",
                stagingBucketUuid,
                tempBucketUuid)
            .getDataprocCluster();

    assertDataprocCluster(
        cluster,
        workspaceId,
        "asia-east1",
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getDefaultUserEmail());

    mockWorkspaceV1Api.deleteWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION));
  }

  @Test
  public void createDataprocCluster_duplicateInstanceId() throws Exception {
    String duplicateName = "not-unique-name";
    ApiGcpDataprocClusterResource unused =
        mockGcpApi
            .createDataprocClusterAndWait(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                "asia-east1",
                stagingBucketUuid,
                tempBucketUuid,
                duplicateName)
            .getDataprocCluster();

    ApiErrorReport errorReport =
        mockGcpApi
            .createDataprocClusterAndExpect(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                "asia-east1",
                stagingBucketUuid,
                tempBucketUuid,
                duplicateName,
                StatusEnum.FAILED)
            .getErrorReport();

    assertEquals("A resource with matching attributes already exists", errorReport.getMessage());
  }

  private void assertDataprocCluster(
      ApiGcpDataprocClusterResource actualResource,
      UUID expectedWorkspaceId,
      String expectedRegion,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.DATAPROC_CLUSTER,
        ApiStewardshipType.CONTROLLED,
        actualResource.getMetadata().getCloningInstructions(),
        expectedWorkspaceId,
        actualResource.getMetadata().getName(),
        actualResource.getMetadata().getDescription(),
        /* expectedResourceLineage= */ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertControlledResourceMetadata(
        actualResource.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.PRIVATE_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser().userName(expectedCreatedBy),
        ApiPrivateResourceState.ACTIVE,
        expectedRegion);
  }
}
