package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.TestLandingZoneManager;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class AzureWorkspaceTest extends BaseAzureConnectedTest {

  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private LandingZoneTestUtils landingZoneTestUtils;
  @Autowired private LandingZoneDao landingZoneDao;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CrlService crlService;
  @Autowired private AzureConfiguration azureConfig;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @MockBean private SamService mockSamService;

  @Test
  void createGetDeleteAzureContext() {
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest()
            .token(Optional.of("fake-token"))
            .email("fake@email.com")
            .subjectId("fakeID123");

    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();

    workspaceService.createWorkspace(workspace, null, null, userRequest);

    String jobId = UUID.randomUUID().toString();
    workspaceService.createAzureCloudContext(
        workspace, jobId, userRequest, "/fake/value");
    jobService.waitForJob(jobId);

    assertNull(jobService.retrieveJobResult(jobId, Object.class).getException());
    assertTrue(
        azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isPresent());
    workspaceService.deleteAzureCloudContext(workspace, userRequest);
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());
  }

  @Test
  @Disabled
  // TODO: TOAZ-286 - problem with LZ configuration
  void cloneAzureWorkspaceWithContainer() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    Mockito.when(mockSamService.getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(userRequest.getEmail());

    SpendProfileId spendProfileId = new SpendProfileId(UUID.randomUUID().toString());

    UUID sourceUUID = UUID.randomUUID();
    Workspace sourceWorkspace =
        Workspace.builder()
            .workspaceId(sourceUUID)
            .userFacingId("a" + sourceUUID)
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();

    workspaceService.createWorkspace(sourceWorkspace, null, null, userRequest);

    String jobId = UUID.randomUUID().toString();

    workspaceService.createAzureCloudContext(
        sourceWorkspace, jobId, userRequest, "/fake/value");
    jobService.waitForJob(jobId);

    assertTrue(
        azureCloudContextService
            .getAzureCloudContext(sourceWorkspace.getWorkspaceId())
            .isPresent());

    UUID landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());
    String storageAccountName = String.format("lzsharedstacc%s", TestUtils.getRandomString(6));

    TestLandingZoneManager testLandingZoneManager =
        new TestLandingZoneManager(
            azureCloudContextService,
            landingZoneDao,
            workspaceDao,
            crlService,
            azureConfig,
            sourceWorkspace.getWorkspaceId());

    testLandingZoneManager.createLandingZoneWithSharedStorageAccount(
        landingZoneId, sourceWorkspace.getWorkspaceId(), storageAccountName, "eastus");

    int timeout = 0;
    while (crlService
        .getStorageManager(azureTestUtils.getAzureCloudContext(), azureConfig)
        .storageAccounts()
        .list()
        .stream()
        .noneMatch(storageAccount -> storageAccount.name().equals(storageAccountName))) {
      if (timeout++ > 60) {
        fail("Landing zone storage account never finished creating");
      }
      TimeUnit.SECONDS.sleep(1);
    }

    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(sourceWorkspace.getWorkspaceId())
                    .resourceId(containerResourceId)
                    .name(storageContainerName)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .iamRole(ControlledResourceIamRole.OWNER)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .createdByEmail(userRequest.getEmail())
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .build())
            .storageContainerName(storageContainerName)
            .build();

    controlledResourceService.createControlledResourceSync(
        containerResource,
        ControlledResourceIamRole.OWNER,
        userRequest,
        new ApiAzureStorageContainerCreationParameters()
            .storageContainerName("storageContainerName"));

    UUID destUUID = UUID.randomUUID();
    Workspace destWorkspace =
        Workspace.builder()
            .workspaceId(destUUID)
            .userFacingId("a" + destUUID)
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();
    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userRequest,
            null,
            destWorkspace,
            azureTestUtils.getAzureCloudContext());
    jobService.waitForJob(cloneJobId);

    assertEquals(workspaceService.getWorkspace(destUUID), destWorkspace);
    assertTrue(
        azureCloudContextService.getAzureCloudContext(destWorkspace.getWorkspaceId()).isPresent());
    assertEquals(
        wsmResourceService
            .enumerateResources(
                destWorkspace.getWorkspaceId(),
                WsmResourceFamily.AZURE_STORAGE_CONTAINER,
                StewardshipType.CONTROLLED,
                0,
                100)
            .size(),
        1);
  }
}
