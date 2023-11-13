package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureManagedIdentityCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
@TestInstance(Lifecycle.PER_CLASS)
public class AzureCloneWorkspaceTest extends BaseAzureConnectedTest {
  @Autowired private JobService jobService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private UserAccessUtils userAccessUtils;

  private Workspace sourceWorkspace = null;
  private Workspace destWorkspace = null;

  @BeforeAll
  public void setup() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    sourceWorkspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
  }

  @AfterAll
  void cleanup() {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    Optional.ofNullable(sourceWorkspace)
        .ifPresent(workspace -> workspaceService.deleteWorkspace(workspace, userRequest));
    Optional.ofNullable(destWorkspace)
        .ifPresent(workspace -> workspaceService.deleteWorkspace(workspace, userRequest));
  }

  @Test
  void cloneAzureWorkspace() {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    UUID containerResourceId = UUID.randomUUID();
    String storageContainerName = ControlledAzureResourceFixtures.uniqueStorageContainerName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(sourceWorkspace.getWorkspaceId())
                    .resourceId(containerResourceId)
                    .name(storageContainerName)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .iamRole(ControlledResourceIamRole.OWNER)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .createdByEmail(userRequest.getEmail())
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .region(DEFAULT_AZURE_RESOURCE_REGION)
                    .build())
            .storageContainerName(storageContainerName)
            .build();

    controlledResourceService.createControlledResourceSync(
        containerResource,
        ControlledResourceIamRole.OWNER,
        userRequest,
        new ApiAzureStorageContainerCreationParameters()
            .storageContainerName("storageContainerName"));

    String managedIdentityResourceName = "idfoobar";
    String managedIdentityName = "id%s".formatted(UUID.randomUUID().toString());

    ControlledAzureManagedIdentityResource managedIdentityResource =
        ControlledAzureManagedIdentityResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(sourceWorkspace.workspaceId())
                    .name(managedIdentityResourceName)
                    .cloningInstructions(CloningInstructions.COPY_DEFINITION)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .region(DEFAULT_AZURE_RESOURCE_REGION)
                    .build())
            .managedIdentityName(managedIdentityName)
            .build();

    controlledResourceService.createControlledResourceSync(
        managedIdentityResource,
        ControlledResourceIamRole.OWNER,
        userRequest,
        new ApiAzureManagedIdentityCreationParameters().name(managedIdentityName));

    UUID destUUID = UUID.randomUUID();

    destWorkspace =
        Workspace.builder()
            .workspaceId(destUUID)
            .userFacingId("a" + destUUID)
            .spendProfileId(azureTestUtils.getSpendProfileId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();

    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userRequest,
            /*location=*/ null,
            /*additionalPolicies=*/ null,
            destWorkspace,
            azureTestUtils.getSpendProfile(),
            /*projectOwnerGroupId=*/ null);
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
    assertEquals(
        wsmResourceService
            .enumerateResources(
                destWorkspace.getWorkspaceId(),
                WsmResourceFamily.AZURE_MANAGED_IDENTITY,
                StewardshipType.CONTROLLED,
                0,
                100)
            .size(),
        1);
  }
}
