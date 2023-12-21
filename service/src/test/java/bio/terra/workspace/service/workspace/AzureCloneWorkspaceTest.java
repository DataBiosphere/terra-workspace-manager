package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
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
  @Autowired private ResourceDao resourceDao;
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

  ControlledAzureStorageContainerResource createSourceContainer(
      AuthenticatedUserRequest userRequest) {
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

    return containerResource;
  }

  ControlledAzureManagedIdentityResource createSourceManagedIdentity(
      AuthenticatedUserRequest userRequest) {
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

    return managedIdentityResource;
  }

  ControlledAzureDatabaseResource createSourceDatabase(
      AuthenticatedUserRequest userRequest, ControlledAzureManagedIdentityResource owner)
      throws InterruptedException {
    String databaseResourceName = "dbfoobar";
    String databaseName = "db%s".formatted(UUID.randomUUID().toString().replace('-', '_'));

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(databaseName, false)
            .owner(owner.getWsmResourceFields().getName());

    var databaseResource =
        ControlledAzureDatabaseResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(sourceWorkspace.workspaceId())
                    .name(databaseResourceName)
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .region(DEFAULT_AZURE_RESOURCE_REGION)
                    .build())
            .databaseOwner(owner.getWsmResourceFields().getName())
            .databaseName(databaseName)
            .allowAccessForAllWorkspaceUsers(false)
            .build();

    controlledResourceService.createControlledResourceSync(
        databaseResource, ControlledResourceIamRole.OWNER, userRequest, creationParameters);

    return databaseResource;
  }

  void assertXofResourceY(int expectedNumber, WsmResourceFamily resourceType) {
    assertEquals(
        expectedNumber,
        resourceDao
            .enumerateResources(
                destWorkspace.getWorkspaceId(), resourceType, StewardshipType.CONTROLLED, 0, 100)
            .size());
  }

  @Test
  void cloneAzureWorkspace() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    createSourceContainer(userRequest);
    var sourceManagedIdentity = createSourceManagedIdentity(userRequest);
    createSourceDatabase(userRequest, sourceManagedIdentity);

    // Create destination workspace resource
    UUID destUUID = UUID.randomUUID();

    destWorkspace =
        Workspace.builder()
            .workspaceId(destUUID)
            .userFacingId("a" + destUUID)
            .spendProfileId(azureTestUtils.getSpendProfileId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();

    // Clone into destination workspace
    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userRequest,
            /* location= */ null,
            /* additionalPolicies= */ null,
            destWorkspace,
            azureTestUtils.getSpendProfile(),
            /* projectOwnerGroupId= */ null);
    jobService.waitForJob(cloneJobId);

    assertEquals(workspaceService.getWorkspace(destUUID), destWorkspace);
    assertTrue(
        azureCloudContextService.getAzureCloudContext(destWorkspace.getWorkspaceId()).isPresent());
    assertXofResourceY(1, WsmResourceFamily.AZURE_STORAGE_CONTAINER);
    assertXofResourceY(1, WsmResourceFamily.AZURE_MANAGED_IDENTITY);
    assertXofResourceY(1, WsmResourceFamily.AZURE_DATABASE);
  }
}
