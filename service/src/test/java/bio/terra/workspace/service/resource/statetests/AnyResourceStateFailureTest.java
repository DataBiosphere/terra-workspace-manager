package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.mocks.MockDataRepoApi.CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockDataRepoApi.REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockFlexibleResourceApi.CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_REFERENCED_GCP_BQ_DATASETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_REFERENCED_GCP_BQ_DATA_TABLES_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.REFERENCED_GCP_BQ_DATASET_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGitRepoApi.CREATE_REFERENCED_GIT_REPOS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGitRepoApi.REFERENCED_GIT_REPOS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.mocks.MockFlexibleResourceApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class AnyResourceStateFailureTest extends BaseUnitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private MockFlexibleResourceApi mockFlexibleResourceApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReferencedResourceService referencedResourceService;
  @Autowired ResourceDao resourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  private static final String RESOURCE_NAME = "resourcename";
  private StateTestUtils stateTestUtils;
  private ApiReferenceResourceCommonFields refMetadata;

  @BeforeEach
  void setup() throws Exception {
    // Construct after the autowiring is done
    stateTestUtils = new StateTestUtils(mockMvc, mockMvcUtils, mockWorkspaceV1Api);

    // Everything is authorized!
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(true);
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    refMetadata =
        ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi().name(RESOURCE_NAME);
  }

  @Test
  void testNoContextResourceCreateValidation() throws Exception {
    // Fake up a CREATING workspace
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    var flightId = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace, flightId);

    // ANY-Controlled Flexible
    ApiCreateControlledFlexibleResourceRequestBody flexRequest =
        mockFlexibleResourceApi.createFlexibleResourceRequestBody(
            RESOURCE_NAME, "terra", "footype", "foodata".getBytes(StandardCharsets.UTF_8));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(flexRequest),
        CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // ANY-Referenced Data Repo Snapshot
    ApiCreateDataRepoSnapshotReferenceRequestBody tdrRequest =
        new ApiCreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(RESOURCE_NAME))
            .snapshot(
                new ApiDataRepoSnapshotAttributes()
                    .instanceName("terra")
                    .snapshot("fake-snapshot"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(tdrRequest),
        CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // ANY-Referenced Git Repo
    var gitRequest =
        new ApiCreateGitRepoReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(RESOURCE_NAME))
            .gitrepo(new ApiGitRepoAttributes().gitRepoUrl("fake-url"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(gitRequest),
        CREATE_REFERENCED_GIT_REPOS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Referenced BQ dataset
    var refBqDatasetRequest =
        new ApiCreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(refMetadata)
            .dataset(
                new ApiGcpBigQueryDatasetAttributes()
                    .projectId("fake-project-id")
                    .datasetId("fake-dataset"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(refBqDatasetRequest),
        CREATE_REFERENCED_GCP_BQ_DATASETS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Referenced BQ data table
    var refBqDataTableRequest =
        new ApiCreateGcpBigQueryDataTableReferenceRequestBody()
            .metadata(refMetadata)
            .dataTable(
                new ApiGcpBigQueryDataTableAttributes()
                    .projectId("fake-project-id")
                    .datasetId("fake-dataset")
                    .dataTableId("fake-table"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(refBqDataTableRequest),
        CREATE_REFERENCED_GCP_BQ_DATA_TABLES_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Referenced Bucket
    var refBucketRequest =
        new ApiCreateGcpGcsBucketReferenceRequestBody()
            .metadata(refMetadata)
            .bucket(new ApiGcpGcsBucketAttributes().bucketName("fake-bucket"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(refBucketRequest),
        CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);

    // GCP-Referenced Bucket Object
    var refBucketObjectRequest =
        new ApiCreateGcpGcsObjectReferenceRequestBody()
            .metadata(refMetadata)
            .file(new ApiGcpGcsObjectAttributes().bucketName("fake-bucket").fileName("fake-file"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(refBucketObjectRequest),
        CREATE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT.formatted(workspace.workspaceId()),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  void testNoContextResourceModifyValidation() throws Exception {
    // Fake up a workspace
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);

    // Create fake no-context resources

    // ANY-Controlled Flexible
    var flexResource =
        ControlledResourceFixtures.makeDefaultFlexResourceBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, flexResource);

    // ANY-Referenced Data Repo Snapshot
    var tdrResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
    referencedResourceService.createReferenceResource(tdrResource, USER_REQUEST);

    // ANY-Referenced Git Repo
    var gitResource = ReferenceResourceFixtures.makeGitRepoResource(workspaceUuid, "fake-url");
    referencedResourceService.createReferenceResource(gitResource, USER_REQUEST);

    // GCP-Referenced BQ dataset
    var bqResource =
        ReferenceResourceFixtures.makeReferencedBqDatasetResource(
            workspaceUuid, "fake-project", "fakedataset");
    referencedResourceService.createReferenceResource(bqResource, USER_REQUEST);

    // GCP-Referenced BQ data table
    var bqTable =
        ReferenceResourceFixtures.makeReferencedBqDataTableResource(
            workspaceUuid, "fake-project", "fakedataset", "fakefile");
    referencedResourceService.createReferenceResource(bqTable, USER_REQUEST);

    // GCP-Referenced Bucket
    var gcsBucket =
        ReferenceResourceFixtures.makeReferencedGcsBucketResource(workspaceUuid, "fake-bucket");
    referencedResourceService.createReferenceResource(gcsBucket, USER_REQUEST);

    // GCP-Referenced Bucket Object
    var gcsObject =
        ReferenceResourceFixtures.makeReferencedGcsObjectResource(
            workspaceUuid, "fake-bucket", "fake-file");
    referencedResourceService.createReferenceResource(gcsObject, USER_REQUEST);

    // Set workspace into deleting state
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteWorkspaceStart(workspaceUuid, flightId);

    // ANY-Controlled Flexible
    mockFlexibleResourceApi.updateFlexibleResourceExpect(
        workspaceUuid,
        flexResource.getResourceId(),
        null,
        null,
        null,
        null,
        HttpStatus.SC_CONFLICT);
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, flexResource.getResourceId(), REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT);

    // ANY-Referenced Data Repo Snapshot
    var tdrUpdateRequest = new ApiUpdateDataRepoSnapshotReferenceRequestBody().snapshot("foobar");
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        tdrResource.getResourceId(),
        REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT,
        objectMapper.writeValueAsString(tdrUpdateRequest));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, tdrResource.getResourceId(), REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT);

    // ANY-Referenced Git Repo
    var gitUpdateRequest = new ApiUpdateGitRepoReferenceRequestBody().gitRepoUrl("new-fake-url");
    stateTestUtils.patchResourceExpectConflict(
        workspaceUuid,
        gitResource.getResourceId(),
        REFERENCED_GIT_REPOS_PATH_FORMAT,
        objectMapper.writeValueAsString(gitUpdateRequest));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, gitResource.getResourceId(), REFERENCED_GIT_REPOS_PATH_FORMAT);

    // GCP-Referenced BQ dataset
    var bqUpdateRequest = new ApiUpdateBigQueryDatasetReferenceRequestBody().datasetId("foo");
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        bqResource.getResourceId(),
        REFERENCED_GCP_BQ_DATASET_PATH_FORMAT,
        objectMapper.writeValueAsString(bqUpdateRequest));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, bqResource.getResourceId(), REFERENCED_GCP_BQ_DATASET_PATH_FORMAT);

    // GCP-Referenced BQ data table
    var bqTableUpdateRequest =
        new ApiUpdateBigQueryDataTableReferenceRequestBody().datasetId("foo");
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        bqTable.getResourceId(),
        REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT,
        objectMapper.writeValueAsString(bqTableUpdateRequest));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, bqTable.getResourceId(), REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT);

    // GCP-Referenced Bucket
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        bqResource.getResourceId(),
        REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT,
        objectMapper.writeValueAsString(bqTableUpdateRequest));
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, bqResource.getResourceId(), REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT);
  }
}
