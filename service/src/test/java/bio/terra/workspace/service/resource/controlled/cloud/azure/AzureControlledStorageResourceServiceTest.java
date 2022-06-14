package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
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
import bio.terra.workspace.service.workspace.Alpha1Service;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageContainerCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageCreationParameters;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AzureControlledStorageResourceServiceTest extends BaseAzureTest {

  private static Workspace reusableWorkspace;
  private Workspace workspace;
  private UserAccessUtils.TestUser workspaceOwner;
  private UUID storageContainerId;
  private final OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
  private final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(60);

  @Autowired private Alpha1Service alpha1Service;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private AzureControlledStorageResourceService azureControlledStorageResourceService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private SamService samService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private FeatureConfiguration features;
  @Autowired private AzureTestConfiguration azureConfiguration;

  /**
   * Retrieve or create a workspace ready for controlled Azure storage resources.
   *
   * <p>Reusing a workspace saves time between tests.
   *
   * @param user
   */
  private Workspace reusableWorkspace(UserAccessUtils.TestUser user) throws Exception {
    if (AzureControlledStorageResourceServiceTest.reusableWorkspace == null) {
      UUID workspaceUuid =
          workspaceService.createWorkspace(
              Workspace.builder()
                  .workspaceId(UUID.randomUUID())
                  .userFacingId("a" + UUID.randomUUID().toString())
                  .spendProfileId(spendUtils.defaultSpendId())
                  .workspaceStage(WorkspaceStage.MC_WORKSPACE)
                  .build(),
              user.getAuthenticatedRequest());
      AzureControlledStorageResourceServiceTest.reusableWorkspace =
          workspaceService.getWorkspace(workspaceUuid, user.getAuthenticatedRequest());

      workspaceService.createAzureCloudContext(
          workspaceUuid,
          "job-id-123",
          user.getAuthenticatedRequest(),
          null,
          new AzureCloudContext(
              azureConfiguration.getTenantId(),
              azureConfiguration.getSubscriptionId(),
              azureConfiguration.getManagedResourceGroupId()));
      StairwayTestUtils.pollUntilComplete(
          "job-id-123", jobService.getStairway(), Duration.ofSeconds(30), Duration.ofSeconds(300));

      UUID storageAccountId = UUID.randomUUID();
      ControlledAzureStorageResource storageAccount =
          new ControlledAzureStorageResource(
              workspaceUuid,
              storageAccountId,
              "sa-" + workspaceUuid.toString(),
              "",
              CloningInstructions.COPY_NOTHING,
              null,
              PrivateResourceState.NOT_APPLICABLE,
              AccessScopeType.ACCESS_SCOPE_SHARED,
              ManagedByType.MANAGED_BY_USER,
              null,
              "sa" + storageAccountId.toString().substring(0, 6),
              "eastus");
      controlledResourceService.createControlledResourceSync(
          storageAccount,
          null,
          user.getAuthenticatedRequest(),
          getAzureStorageCreationParameters());

      storageContainerId = UUID.randomUUID();
      ControlledAzureStorageContainerResource storageContainer =
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
          user.getAuthenticatedRequest(),
          getAzureStorageContainerCreationParameters());
    }
    return AzureControlledStorageResourceServiceTest.reusableWorkspace;
  }

  private void resetSecondUserWorkspaceAccess(UUID workspaceUuid) throws Exception {
    try {
      List<RoleBinding> roles =
          SamRethrow.onInterrupted(
              () ->
                  samService.listRoleBindings(
                      workspaceUuid, userAccessUtils.secondUserAuthRequest()),
              "listRoleBindings");
      for (RoleBinding role : roles) {
        workspaceService.removeWorkspaceRoleFromUser(
            workspaceUuid,
            role.role(),
            userAccessUtils.getSecondUserEmail(),
            workspaceOwner.getAuthenticatedRequest());
      }
    } catch (ForbiddenException ignored) {
      // User already has no access to workspace
    }
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

  /**
   * Set up default values for user, workspace for tests to use. By default, this will point to the
   * reusable workspace created by {@code reusableWorkspace}.
   */
  @BeforeEach
  public void setupUserAndWorkspace() throws Exception {
    workspaceOwner = userAccessUtils.defaultUser();
    workspace = reusableWorkspace(workspaceOwner);
    resetSecondUserWorkspaceAccess(workspace.getWorkspaceId());
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    // Exercise enumeration by dumping after each
    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(
        alpha1Service, workspace.getWorkspaceId(), workspaceOwner.getAuthenticatedRequest());

    jobService.setFlightDebugInfoForTest(null);
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  private void cleanUpSharedWorkspace() {
    workspaceOwner = userAccessUtils.defaultUser();
    workspaceService.deleteWorkspace(
        workspace.getWorkspaceId(), workspaceOwner.getAuthenticatedRequest());
  }

  @Test
  void createSasTokenForOwner() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();

    String sas =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainerId,
            startTime,
            expiryTime,
            workspaceOwner.getAuthenticatedRequest());

    BlobContainerSasPermission ownerPermissions = BlobContainerSasPermission.parse("rlacwd");
    assertValidToken(sas, ownerPermissions);
  }

  @Test
  void createSasTokenNoAccess() throws Exception {
    assertThrows(
        ForbiddenException.class,
        () ->
            azureControlledStorageResourceService.createAzureStorageContainerSasToken(
                workspace.getWorkspaceId(),
                storageContainerId,
                startTime,
                expiryTime,
                userAccessUtils.secondUserAuthRequest()));
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
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspace.getWorkspaceId(),
            storageContainerId,
            startTime,
            expiryTime,
            userAccessUtils.secondUserAuthRequest());

    BlobContainerSasPermission readerPermissions = BlobContainerSasPermission.parse("rl");
    assertValidToken(sas, readerPermissions);
  }
}
