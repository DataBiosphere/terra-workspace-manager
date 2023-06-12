package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.utils.MockMvcUtils.AZURE_BATCH_POOL_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.AZURE_DISK_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.AZURE_STORAGE_CONTAINER_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CLONE_AZURE_STORAGE_CONTAINER_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_BATCH_POOL_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_DISK_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_STORAGE_CONTAINERS_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCloneControlledAzureStorageContainerRequest;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureBatchPoolRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageContainerRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceRequest;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import com.azure.core.management.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class AzureResourceStateFailureTest extends BaseUnitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReferencedResourceService referencedResourceService;
  @Autowired ResourceDao resourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  @MockBean LandingZoneService mockLandingZoneService;

  private final UUID billingProfileUuid = UUID.randomUUID();
  private final SpendProfileId billingProfileId = new SpendProfileId(billingProfileUuid.toString());
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
    when(mockLandingZoneService.getLandingZonesByBillingProfile(any(), any()))
        .thenReturn(
            List.of(
                LandingZone.builder()
                    .landingZoneId(UUID.randomUUID())
                    .billingProfileId(billingProfileUuid)
                    .definition("definition")
                    .version("1")
                    .createdDate(Instant.now().atOffset(ZoneOffset.UTC))
                    .build()));
    when(mockLandingZoneService.getLandingZoneRegion(any(), any()))
        .thenReturn(Region.GERMANY_CENTRAL.name());
  }

  @Test
  void testAzureContextResourceCreateValidation() throws Exception {
    // Fake up a READY workspace
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace(billingProfileId);
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.AZURE, billingProfileId, createContextFlightId);

    // AZURE-Controlled Batch
    var batchRequest =
        new ApiCreateControlledAzureBatchPoolRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .azureBatchPool(
                ControlledAzureResourceFixtures.createAzureBatchPoolWithRequiredParameters());
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(batchRequest),
        CREATE_AZURE_BATCH_POOL_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);

    // AZURE-Controlled Disk
    var diskRequest =
        new ApiCreateControlledAzureDiskRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .azureDisk(ControlledAzureResourceFixtures.getAzureDiskCreationParameters());
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(diskRequest),
        CREATE_AZURE_DISK_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);

    // AZURE-Storage Container
    var storageRequest =
        new ApiCreateControlledAzureStorageContainerRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .azureStorageContainer(
                ControlledAzureResourceFixtures.getAzureStorageContainerCreationParameters());
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(storageRequest),
        CREATE_AZURE_STORAGE_CONTAINERS_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);

    // AZURE-VM
    var vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .azureVm(ControlledAzureResourceFixtures.getAzureVmCreationParameters());
    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(vmRequest),
        CREATE_AZURE_VM_PATH_FORMAT.formatted(workspaceUuid),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  void testAzureResourceModifyValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace(billingProfileId);
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    WorkspaceUnitTestUtils.createAzureCloudContextInDatabase(
        workspaceDao, workspaceUuid, billingProfileId);

    // Create the resources in the database
    // AZURE-Controlled Batch
    var batchResource =
        ControlledAzureResourceFixtures.makeDefaultAzureBatchPoolResource(workspaceUuid);
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, batchResource);

    // AZURE-Controlled Disk
    var diskResource =
        ControlledAzureResourceFixtures.makeDefaultAzureDiskBuilder(
                ControlledAzureResourceFixtures.getAzureDiskCreationParameters(), workspaceUuid)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, diskResource);

    // AZURE-Storage Container
    var storageResource =
        ControlledAzureResourceFixtures.makeDefaultAzureStorageContainerResourceBuilder(
                workspaceUuid)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, storageResource);

    // AZURE-VM
    var vmResource = ControlledAzureResourceFixtures.makeAzureVm(workspaceUuid);
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, vmResource);

    // Set cloud context info deleting state
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.AZURE, flightId);

    // AZURE-Controlled Batch
    stateTestUtils.deleteResourceExpectConflict(
        workspaceUuid, batchResource.getResourceId(), AZURE_BATCH_POOL_PATH_FORMAT);

    // AZURE-Controlled Disk
    var diskDeleteBody =
        new ApiDeleteControlledAzureResourceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        diskResource.getResourceId(),
        AZURE_DISK_PATH_FORMAT,
        objectMapper.writeValueAsString(diskDeleteBody));

    // AZURE-Storage Container
    var storageDeleteBody =
        new ApiDeleteControlledAzureResourceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        storageResource.getResourceId(),
        AZURE_STORAGE_CONTAINER_PATH_FORMAT,
        objectMapper.writeValueAsString(storageDeleteBody));

    // AZURE-VM
    var vmDeleteBody =
        new ApiDeleteControlledAzureResourceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        vmResource.getResourceId(),
        AZURE_VM_PATH_FORMAT,
        objectMapper.writeValueAsString(vmDeleteBody));
  }

  @Test
  void testAzureResourceCloneValidation() throws Exception {
    // Fake up a READY workspace and a READY cloud context
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace(billingProfileId);
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    WorkspaceUnitTestUtils.createAzureCloudContextInDatabase(
        workspaceDao, workspaceUuid, billingProfileId);

    // AZURE-Storage Container
    var storageResource =
        ControlledAzureResourceFixtures.makeDefaultAzureStorageContainerResourceBuilder(
                workspaceUuid)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, storageResource);

    // Fake up a READY targetWorkspace
    Workspace targetWorkspace = WorkspaceFixtures.createDefaultMcWorkspace();
    WorkspaceFixtures.createWorkspaceInDb(targetWorkspace, workspaceDao);
    // Fake up a CREATING cloud context
    var createContextFlightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        targetWorkspace.workspaceId(),
        CloudPlatform.AZURE,
        billingProfileId,
        createContextFlightId);

    var storageCloneBody =
        new ApiCloneControlledAzureStorageContainerRequest()
            .cloningInstructions(ApiCloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(targetWorkspace.workspaceId())
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    stateTestUtils.postResourceExpectConflict(
        workspaceUuid,
        storageResource.getResourceId(),
        CLONE_AZURE_STORAGE_CONTAINER_PATH_FORMAT,
        objectMapper.writeValueAsString(storageCloneBody));
  }
}
