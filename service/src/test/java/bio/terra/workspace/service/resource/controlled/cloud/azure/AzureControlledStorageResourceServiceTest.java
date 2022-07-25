package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageContainerCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageCreationParameters;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AzureControlledStorageResourceServiceTest extends BaseAzureTest {

  private static Workspace workspace;
  private UserAccessUtils.TestUser workspaceOwner;
  private ControlledAzureStorageResource storageAccount;
  private ControlledAzureStorageContainerResource storageContainer;
  private final OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
  private final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(60);

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private AzureControlledStorageResourceService azureControlledStorageResourceService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private SamService samService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private AzureTestConfiguration azureConfiguration;

  @BeforeEach
  private void resetSecondUserWorkspaceAccess() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();
    List<RoleBinding> roles =
        SamRethrow.onInterrupted(
            () ->
                samService.listRoleBindings(
                    workspaceUuid, workspaceOwner.getAuthenticatedRequest()),
            "listRoleBindings");
    for (RoleBinding role : roles) {
      if (role.users().contains(userAccessUtils.getSecondUserEmail())) {
        workspaceService.removeWorkspaceRoleFromUser(
            workspace,
            role.role(),
            userAccessUtils.getSecondUserEmail(),
            workspaceOwner.getAuthenticatedRequest());
      }
    }
  }

  /**
   * create a workspace, Azure cloud context, and Azure storage account and container.
   *
   * <p>Reusing a workspace saves time between tests.
   */
  @BeforeAll
  private void setupReusableWorkspace() throws Exception {
    workspaceOwner = userAccessUtils.defaultUser();

    UUID workspaceUuid =
        workspaceService.createWorkspace(
            Workspace.builder()
                .workspaceId(UUID.randomUUID())
                .userFacingId(UUID.randomUUID().toString())
                .spendProfileId(spendUtils.defaultSpendId())
                .workspaceStage(WorkspaceStage.MC_WORKSPACE)
                .build(),
            workspaceOwner.getAuthenticatedRequest());
    workspace = workspaceService.getWorkspace(workspaceUuid);

    workspaceService.createAzureCloudContext(
        workspace,
        "job-id-123",
        workspaceOwner.getAuthenticatedRequest(),
        null,
        new AzureCloudContext(
            azureConfiguration.getTenantId(),
            azureConfiguration.getSubscriptionId(),
            azureConfiguration.getManagedResourceGroupId()));
    StairwayTestUtils.pollUntilComplete(
        "job-id-123", jobService.getStairway(), Duration.ofSeconds(30), Duration.ofSeconds(300));

    UUID storageAccountId = UUID.randomUUID();
    String storageAccountName = "sa" + storageAccountId.toString().substring(0, 6);
    storageAccount =
        new ControlledAzureStorageResource(
            workspaceUuid,
            storageAccountId,
            storageAccountName,
            "",
            CloningInstructions.COPY_NOTHING,
            null,
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            null,
            storageAccountName,
            "eastus");
    controlledResourceService.createControlledResourceSync(
        storageAccount,
        null,
        workspaceOwner.getAuthenticatedRequest(),
        getAzureStorageCreationParameters());

    UUID storageContainerId = UUID.randomUUID();
    storageContainer =
        new ControlledAzureStorageContainerResource(
            workspaceUuid,
            storageContainerId,
            "sc-" + workspaceUuid.toString(),
            "",
            CloningInstructions.COPY_NOTHING,
            null,
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            null,
            storageAccount.getResourceId(),
            "sc-" + storageContainerId);
    controlledResourceService.createControlledResourceSync(
        storageContainer,
        null,
        workspaceOwner.getAuthenticatedRequest(),
        getAzureStorageContainerCreationParameters());
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  private void cleanUpSharedWorkspace() {
    workspaceService.deleteWorkspace(workspace, workspaceOwner.getAuthenticatedRequest());
  }

  private void assertValidToken(String sas, BlobContainerSasPermission expectedPermissions) {
    Pattern protocolRegex = Pattern.compile("spr=https&");
    // SAS tokens start and expiry times are UTC
    Pattern startTimeRegex =
        Pattern.compile(
            "st="
                + startTime
                    .atZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE));
    Pattern expiryTimeRegex =
        Pattern.compile(
            "se="
                + expiryTime
                    .atZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE));
    Pattern signedResourceRegex = Pattern.compile("sr=c&");
    Pattern permissionsRegex = Pattern.compile("sp=" + expectedPermissions.toString() + "&");

    assertThat("SAS is https", protocolRegex.matcher(sas).find());
    assertThat("SAS validity starts today", startTimeRegex.matcher(sas).find());
    assertThat("SAS validity ends today", expiryTimeRegex.matcher(sas).find());
    assertThat("SAS is for a container resource", signedResourceRegex.matcher(sas).find());
    assertThat("SAS grants correct permissions", permissionsRegex.matcher(sas).find());
  }

  @Test
  void createSasTokenForOwner() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();

    AzureControlledStorageResourceService.AzureSasBundle sasBundle =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainer,
            storageAccount,
            startTime,
            expiryTime,
            workspaceOwner.getAuthenticatedRequest(),
            null);

    BlobContainerSasPermission ownerPermissions = BlobContainerSasPermission.parse("rlacwd");
    assertValidToken(sasBundle.sasToken(), ownerPermissions);
    assertEquals(
        sasBundle.sasUrl(),
        String.format(
            "https://%s.blob.core.windows.net/sc-%s?%s",
            storageAccount.getStorageAccountName(),
            storageContainer.getResourceId(),
            sasBundle.sasToken()));
  }

  @Test
  void createSasTokenNoAccess() throws Exception {
    assertThrows(
        ForbiddenException.class,
        () ->
            azureControlledStorageResourceService.createAzureStorageContainerSasToken(
                workspace.getWorkspaceId(),
                storageContainer,
                storageAccount,
                startTime,
                expiryTime,
                userAccessUtils.secondUserAuthRequest(),
                null));
  }

  @Test
  void createSasTokenForReader() throws Exception {
    SamRethrow.onInterrupted(
        () ->
            samService.grantWorkspaceRole(
                workspace.getWorkspaceId(),
                workspaceOwner.getAuthenticatedRequest(),
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()),
        "grantWorkspaceRoles");

    String sas =
        azureControlledStorageResourceService
            .createAzureStorageContainerSasToken(
                workspace.getWorkspaceId(),
                storageContainer,
                storageAccount,
                startTime,
                expiryTime,
                userAccessUtils.secondUserAuthRequest(),
                null)
            .sasToken();

    BlobContainerSasPermission readerPermissions = BlobContainerSasPermission.parse("rl");
    assertValidToken(sas, readerPermissions);
  }

  @Test
  void createSasTokenWithIpRange() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();
    var ipRange = "168.1.5.60-168.1.5.70";

    AzureControlledStorageResourceService.AzureSasBundle sasBundle =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainer,
            storageAccount,
            startTime,
            expiryTime,
            workspaceOwner.getAuthenticatedRequest(),
            ipRange);

    BlobContainerSasPermission ownerPermissions = BlobContainerSasPermission.parse("rlacwd");
    assertValidToken(sasBundle.sasToken(), ownerPermissions);
    assertTrue(
        sasBundle.sasToken().contains("sip=" + ipRange),
        "the SignedIP was added to the query parameters");

    assertEquals(
        sasBundle.sasUrl(),
        String.format(
            "https://%s.blob.core.windows.net/sc-%s?%s",
            storageAccount.getStorageAccountName(),
            storageContainer.getResourceId(),
            sasBundle.sasToken()));
  }
}
