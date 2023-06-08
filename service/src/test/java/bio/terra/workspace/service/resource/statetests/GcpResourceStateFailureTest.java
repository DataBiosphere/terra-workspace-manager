package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultNotebookCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_AI_NOTEBOOK_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class GcpResourceStateFailureTest extends BaseUnitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReferencedResourceService referencedResourceService;
  @Autowired ResourceDao resourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  @MockBean private LandingZoneService mockLandingZoneService;

  private static final String RESOURCE_NAME = "resourcename";
  private StateTestUtils stateTestUtils;

  @BeforeEach
  void setup() throws Exception {
    stateTestUtils = new StateTestUtils(mockMvc, mockMvcUtils);
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
        WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID,
        createContextFlightId);

    // GCP-Controlled Notebook
    var notebookRequest =
        new ApiCreateControlledGcpAiNotebookInstanceRequestBody()
            .common(
                makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("ai-notebook")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .aiNotebookInstance(
                defaultNotebookCreationParameters()
                    .instanceId(TestUtils.appendRandomNumber("instance-id")));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(notebookRequest),
        CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled BigQuery
    var bqParameters = new ApiGcpBigQueryDatasetCreationParameters().datasetId("fake-dataset");
    var bqRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(makeDefaultControlledResourceFieldsApi().name(RESOURCE_NAME))
            .dataset(bqParameters);
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(bqRequest),
        CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Controlled Bucket
    var bucketRequest =
        new ApiCreateControlledGcpGcsBucketRequestBody()
            .common(makeDefaultControlledResourceFieldsApi().name(RESOURCE_NAME))
            .gcsBucket(new ApiGcpGcsBucketCreationParameters().name("fake-bucket-name"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(bucketRequest),
        CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  void testGcpResourceModifyValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    UUID workspaceUuid = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    // Create the resources in the database
    // GCP-Controlled Notebook
    var notebookResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, notebookResource);

    // GCP-Controlled BigQuery
    var bqResource =
        ControlledResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bqResource);

    // GCP-Controlled Bucket
    var bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bucketResource);

    // Set cloud context info deleting state
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);

    // GCP-Controlled Notebook
    var notebookRequestBody =
        new ApiUpdateControlledGcpAiNotebookInstanceRequestBody().name("foobar");
    stateTestUtils.updateControlledResource(
        ApiGcpBigQueryDatasetResource.class,
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_AI_NOTEBOOK_V1_PATH_FORMAT,
        objectMapper.writeValueAsString(notebookRequestBody));
    var notebookDeleteBody =
        new ApiDeleteControlledGcpAiNotebookInstanceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_AI_NOTEBOOK_V1_PATH_FORMAT,
        objectMapper.writeValueAsString(notebookDeleteBody));

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
        CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT,
        objectMapper.writeValueAsString(bqRequestBody));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, bqResource.getResourceId(), CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);

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
        CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT,
        objectMapper.writeValueAsString(bucketRequestBody));
    var bucketDeleteBody =
        new ApiDeleteControlledGcpGcsBucketRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT,
        objectMapper.writeValueAsString(bucketDeleteBody));
  }

  @Test
  void testGcpResourceCloneValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    UUID workspaceUuid = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    // GCP-Controlled BigQuery
    var bqResource =
        ControlledResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bqResource);

    // GCP-Controlled Bucket
    var bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bucketResource);

    // Fake up a READY targetWorkspace
    Workspace targetWorkspace = WorkspaceFixtures.createDefaultMcWorkspace();
    WorkspaceFixtures.createWorkspaceInDb(targetWorkspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        targetWorkspace.getWorkspaceId(),
        CloudPlatform.GCP,
        WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID,
        createContextFlightId);

    mockMvcUtils.cloneControlledBqDatasetAsync(
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

    mockMvcUtils.cloneControlledGcsBucketAsync(
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
