package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpDataprocClusterRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGceInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class GcpResourceStateFailureTest extends BaseSpringBootUnitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private MockGcpApi mockGcpApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReferencedResourceService referencedResourceService;
  @Autowired ResourceDao resourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  private static final String RESOURCE_NAME = "resourcename";
  private StateTestUtils stateTestUtils;

  @BeforeEach
  void setup() throws Exception {
    stateTestUtils = new StateTestUtils(mockMvc, mockMvcUtils, mockWorkspaceV1Api);
    // Everything is authorized!
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(true);
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());
  }

  @Test
  void testGcpContextResourceCreateValidation() throws Exception {
    // Fake up a READY workspace
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspace.getWorkspaceId(),
        CloudPlatform.GCP,
        WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID,
        createContextFlightId);

    // GCP-Controlled Instance
    var instanceRequest =
        new ApiCreateControlledGcpGceInstanceRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("gce-instance")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .gceInstance(
                ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
                    .instanceId(TestUtils.appendRandomNumber("instance-id")));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(instanceRequest),
        CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled Notebook
    var notebookRequest =
        new ApiCreateControlledGcpAiNotebookInstanceRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("ai-notebook")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .aiNotebookInstance(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
                    .instanceId(TestUtils.appendRandomNumber("instance-id")));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(notebookRequest),
        CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled Dataproc cluster
    var clusterRequest =
        new ApiCreateControlledGcpDataprocClusterRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("dataproc-cluster")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .dataprocCluster(
                ControlledGcpResourceFixtures.defaultDataprocClusterCreationParameters()
                    .clusterId(TestUtils.appendRandomNumber("cluster-id"))
                    .configBucket(UUID.randomUUID())
                    .tempBucket(UUID.randomUUID()));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(clusterRequest),
        CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled BigQuery
    var bqParameters = new ApiGcpBigQueryDatasetCreationParameters().datasetId("fake-dataset");
    var bqRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(RESOURCE_NAME))
            .dataset(bqParameters);
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(bqRequest),
        CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled Bucket
    var bucketRequest =
        new ApiCreateControlledGcpGcsBucketRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(RESOURCE_NAME))
            .gcsBucket(new ApiGcpGcsBucketCreationParameters().name("fake-bucket-name"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(bucketRequest),
        CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  void testGcpResourceModifyValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    UUID workspaceUuid = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    // Create the resources in the database
    // GCP-Controlled Instance
    var instanceResource =
        ControlledGcpResourceFixtures.makeDefaultGceInstance(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, instanceResource);

    // GCP-Controlled Notebook
    var notebookResource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, notebookResource);

    // GCP-Controlled BigQuery
    var bqResource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bqResource);

    // GCP-Controlled Bucket
    var bucketResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bucketResource);

    // Set cloud context info deleting state
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);

    // GCP-Controlled Instance
    var instanceRequestBody = new ApiUpdateControlledGcpGceInstanceRequestBody().name("foobar");
    stateTestUtils.updateControlledResource(
        ApiGcpGceInstanceResource.class,
        workspaceUuid,
        instanceResource.getResourceId(),
        CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT,
        objectMapper.writeValueAsString(instanceRequestBody));
    var instanceDeleteBody =
        new ApiDeleteControlledGcpGceInstanceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        instanceResource.getResourceId(),
        CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT,
        objectMapper.writeValueAsString(instanceDeleteBody));

    // GCP-Controlled Notebook
    var notebookRequestBody =
        new ApiUpdateControlledGcpAiNotebookInstanceRequestBody().name("foobar");
    stateTestUtils.updateControlledResource(
        ApiGcpAiNotebookInstanceResource.class,
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT,
        objectMapper.writeValueAsString(notebookRequestBody));
    var notebookDeleteBody =
        new ApiDeleteControlledGcpAiNotebookInstanceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT,
        objectMapper.writeValueAsString(notebookDeleteBody));

    // GCP-Controlled Dataproc cluster
    var clusterRequestBody = new ApiUpdateControlledGcpDataprocClusterRequestBody().name("foobar");
    var clusterResource =
        ControlledGcpResourceFixtures.makeDefaultDataprocCluster(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, clusterResource);
    stateTestUtils.updateControlledResource(
        ApiGcpDataprocClusterResource.class,
        workspaceUuid,
        clusterResource.getResourceId(),
        CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT,
        objectMapper.writeValueAsString(clusterRequestBody));

    var clusterDeleteBody =
        new ApiDeleteControlledGcpDataprocClusterRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        clusterResource.getResourceId(),
        CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT,
        objectMapper.writeValueAsString(clusterDeleteBody));

    // GCP-Controlled BigQuery
    var bqRequestBody =
        new ApiUpdateControlledGcpBigQueryDatasetRequestBody()
            .updateParameters(
                new ApiGcpBigQueryDatasetUpdateParameters()
                    .cloningInstructions(ApiCloningInstructionsEnum.RESOURCE));
    stateTestUtils.updateControlledResource(
        ApiGcpBigQueryDatasetResource.class,
        workspaceUuid,
        bqResource.getResourceId(),
        CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT,
        objectMapper.writeValueAsString(bqRequestBody));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, bqResource.getResourceId(), CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT);

    // GCP-Controlled bucket
    var bucketRequestBody =
        new ApiUpdateControlledGcpGcsBucketRequestBody()
            .updateParameters(
                new ApiGcpGcsBucketUpdateParameters()
                    .cloningInstructions(ApiCloningInstructionsEnum.RESOURCE));
    stateTestUtils.updateControlledResource(
        ApiGcpGcsBucketResource.class,
        workspaceUuid,
        bucketResource.getResourceId(),
        CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
        objectMapper.writeValueAsString(bucketRequestBody));
    var bucketDeleteBody =
        new ApiDeleteControlledGcpGcsBucketRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
        objectMapper.writeValueAsString(bucketDeleteBody));
  }

  @Test
  void testGcpResourceCloneValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    UUID workspaceUuid = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    // GCP-Controlled BigQuery
    var bqResource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bqResource);

    // GCP-Controlled Bucket
    var bucketResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bucketResource);

    // Fake up a READY targetWorkspace
    Workspace targetWorkspace = WorkspaceFixtures.createDefaultMcWorkspace();
    WorkspaceFixtures.createWorkspaceInDb(targetWorkspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        targetWorkspace.getWorkspaceId(),
        CloudPlatform.GCP,
        WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID,
        createContextFlightId);

    mockGcpApi.cloneControlledBqDatasetAsyncAndExpect(
        USER_REQUEST,
        workspaceUuid,
        bqResource.getResourceId(),
        targetWorkspace.workspaceId(),
        ApiCloningInstructionsEnum.DEFINITION,
        null,
        null,
        null,
        null,
        null,
        Collections.singletonList(HttpStatus.SC_CONFLICT),
        false);

    mockGcpApi.cloneControlledGcsBucketAsyncAndExpect(
        USER_REQUEST,
        workspaceUuid,
        bucketResource.getResourceId(),
        targetWorkspace.workspaceId(),
        ApiCloningInstructionsEnum.DEFINITION,
        null,
        null,
        null,
        Collections.singletonList(HttpStatus.SC_CONFLICT),
        false);
  }
}
