package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageContainerCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getAzureStorageCreationParameters;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AzureControlledStorageResourceServiceTest extends BaseAzureTest {

  private Workspace workspace;
  private UserAccessUtils.TestUser user;
  @Autowired private Alpha1Service alpha1Service;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private AzureControlledStorageResourceService azureControlledStorageResourceService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private FeatureConfiguration features;
  @Autowired private AzureTestConfiguration azureConfiguration;

  /**
   * Set up default values for user, workspace, and projectId for tests to use. By default, this
   * will point to the reusable workspace created by {@code reusableWorkspace}.
   */
  @BeforeEach
  public void setupUserAndWorkspace() {
    user = userAccessUtils.defaultUser();
    UUID workspaceUuid =
        workspaceService.createWorkspace(
            Workspace.builder()
                .workspaceId(UUID.randomUUID())
                .userFacingId("a" + UUID.randomUUID().toString())
                .spendProfileId(spendUtils.defaultSpendId())
                .workspaceStage(WorkspaceStage.MC_WORKSPACE)
                .build(),
            user.getAuthenticatedRequest());
    workspace = workspaceService.getWorkspace(workspaceUuid, user.getAuthenticatedRequest());
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    // Exercise enumeration by dumping after each
    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(
        alpha1Service, workspace.getWorkspaceId(), user.getAuthenticatedRequest());

    jobService.setFlightDebugInfoForTest(null);
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  private void cleanUpSharedWorkspace() {
    user = userAccessUtils.defaultUser();
    workspaceService.deleteWorkspace(workspace.getWorkspaceId(), user.getAuthenticatedRequest());
  }

  @Test
  void createSasToken() throws Exception {
    UUID workspaceUuid = workspace.getWorkspaceId();
    workspaceService.createWorkspace(workspace, user.getAuthenticatedRequest());
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
        storageAccount, null, user.getAuthenticatedRequest(), getAzureStorageCreationParameters());

    UUID storageContainerId = UUID.randomUUID();
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

    OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
    OffsetDateTime expiryTime = OffsetDateTime.now().plusHours(1);
    String sas =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainer.getResourceId(),
            startTime,
            expiryTime,
            user.getAuthenticatedRequest());
    assertThat("sas is formatted correctly", sas.startsWith("sv"));

    assertThrows(
        ForbiddenException.class,
        () ->
            azureControlledStorageResourceService.createAzureStorageContainerSasToken(
                workspaceUuid,
                storageContainer.getResourceId(),
                startTime,
                expiryTime,
                userAccessUtils.secondUserAuthRequest()));
  }
}
