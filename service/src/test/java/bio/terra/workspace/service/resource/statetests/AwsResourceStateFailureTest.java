package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.mocks.MockAwsApi.CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockAwsApi.CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockAwsApi.CREATE_CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockAwsApi.CREATE_CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsSageMakerNotebookRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class AwsResourceStateFailureTest extends BaseAwsSpringBootUnitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReferencedResourceService referencedResourceService;
  @Autowired ResourceDao resourceDao;
  @Autowired private WorkspaceDao workspaceDao;
  private final UUID billingProfileUuid = UUID.randomUUID();
  private final SpendProfileId billingProfileId = new SpendProfileId(billingProfileUuid.toString());
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
  void testAwsContextResourceCreateValidation() throws Exception {
    // Fake up a READY workspace
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace(billingProfileId);
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.AWS, billingProfileId, createContextFlightId);

    // AWS-storage
    var storageRequest =
        new ApiCreateControlledAwsS3StorageFolderRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .awsS3StorageFolder(new ApiAwsS3StorageFolderCreationParameters().folderName("foo"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(storageRequest),
        CREATE_CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);

    // AWS-notebook
    var vmRequest =
        new ApiCreateControlledAwsSageMakerNotebookRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(ApiAccessScope.PRIVATE_ACCESS))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .awsSageMakerNotebook(
                new ApiAwsSageMakerNotebookCreationParameters().instanceName("foo"));
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(vmRequest),
        CREATE_CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  void testAwsResourceModifyValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace(billingProfileId);
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    WorkspaceUnitTestUtils.createAwsCloudContextInDatabase(
        workspaceDao, workspaceUuid, billingProfileId);

    // Create the resources in the database
    // AWS-Storage
    var storageResource =
        ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(workspaceUuid);
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, storageResource);

    // AWS-Notebook
    var notebookResource =
        ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(workspaceUuid);
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, notebookResource);

    // Set cloud context info deleting state
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.AWS, flightId);

    // AWS-Storage
    var storageDeleteBody =
        new ApiDeleteControlledAwsResourceRequestBody()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        storageResource.getResourceId(),
        CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT,
        objectMapper.writeValueAsString(storageDeleteBody));

    // AWS-Notebook
    var notebookDeleteBody =
        new ApiDeleteControlledAwsResourceRequestBody()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        notebookResource.getResourceId(),
        CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT,
        objectMapper.writeValueAsString(notebookDeleteBody));
  }
}
