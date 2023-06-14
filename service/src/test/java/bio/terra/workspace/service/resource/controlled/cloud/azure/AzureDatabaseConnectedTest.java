package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.resourcemanager.msi.models.Identity;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Database;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

// @Tag("azureConnected") - this test is tagged at the individual test level
@TestInstance(Lifecycle.PER_CLASS)
public class AzureDatabaseConnectedTest extends BaseAzureConnectedTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private LandingZoneApiDispatch landingZoneApiDispatch;
  @Autowired private SamService samService;

  private Workspace sharedWorkspace;
  private UUID workspaceUuid;

  @BeforeAll
  public void setup() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    sharedWorkspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
    workspaceUuid = sharedWorkspace.getWorkspaceId();
  }

  @AfterAll
  public void cleanup() {
    // Deleting the workspace will also delete any resources contained in the workspace, including
    // VMs and the resources created during setup.
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Tag("azureConnected")
  @Test
  public void createAndDeleteAzureManagedIdAndDatabase() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    var uamiResource = createManagedIdentity(userRequest);
    var dbResource = createDatabase(userRequest, uamiResource);

    // Verify that the resources we created
    var resourceList = wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, uamiResource);
    checkForResource(resourceList, dbResource);

    // wait for azure to sync then make sure the resources actually exist
    TimeUnit.SECONDS.sleep(5);
    var actualUami =
        getManagedIdentityFunction()
            .apply(
                azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                uamiResource.getManagedIdentityName());
    assertNotNull(actualUami);

    var actualDatabase =
        getDatabaseFunction()
            .apply(
                azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                dbResource.getDatabaseName());
    assertNotNull(actualDatabase);

    deleteDatabase(userRequest, dbResource);
    deleteManagedIdentity(userRequest, uamiResource);
  }

  private void deleteDatabase(
      AuthenticatedUserRequest userRequest, ControlledAzureDatabaseResource dbResource)
      throws InterruptedException {
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        dbResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        dbResource.getDatabaseName(),
        getDatabaseFunction());
  }

  @NotNull
  private BiFunction<String, String, Database> getDatabaseFunction() {
    var dbServerName =
        landingZoneApiDispatch
            .getSharedDatabase(
                new BearerToken(samService.getWsmServiceAccountToken()), landingZoneId)
            .map(
                r -> {
                  var parts = r.getResourceId().split("/");
                  return parts[parts.length - 1];
                })
            .orElseThrow();
    final BiFunction<String, String, Database> getDatabaseFunction =
        (resourceGroup, resourceName) ->
            azureTestUtils
                .getPostgreSqlManager()
                .databases()
                .get(resourceGroup, dbServerName, resourceName);
    return getDatabaseFunction;
  }

  private void deleteManagedIdentity(
      AuthenticatedUserRequest userRequest, ControlledAzureManagedIdentityResource uamiResource)
      throws InterruptedException {
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        uamiResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        uamiResource.getManagedIdentityName(),
        getManagedIdentityFunction());
  }

  @NotNull
  private BiFunction<String, String, Identity> getManagedIdentityFunction() {
    return azureTestUtils.getMsiManager().identities()::getByResourceGroup;
  }

  private ControlledAzureDatabaseResource createDatabase(
      AuthenticatedUserRequest userRequest, ControlledAzureManagedIdentityResource uamiResource)
      throws InterruptedException {
    var dbCreationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            uamiResource.getResourceId());

    var dbResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureDatabaseResourceBuilder(
                dbCreationParameters, workspaceUuid)
            .build();

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        dbResource,
        WsmResourceType.CONTROLLED_AZURE_DATABASE,
        dbCreationParameters);
    return dbResource;
  }

  private ControlledAzureManagedIdentityResource createManagedIdentity(
      AuthenticatedUserRequest userRequest) throws InterruptedException {
    var uamiCreationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();

    var uamiResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                uamiCreationParameters, workspaceUuid)
            .build();

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        uamiResource,
        WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY,
        uamiCreationParameters);
    return uamiResource;
  }

  private void checkForResource(List<WsmResource> resourceList, ControlledResource resource) {
    for (WsmResource wsmResource : resourceList) {
      if (wsmResource.getResourceId().equals(resource.getResourceId())) {
        assertEquals(resource.getResourceType(), wsmResource.getResourceType());
        assertEquals(resource.getWorkspaceId(), wsmResource.getWorkspaceId());
        assertEquals(resource.getName(), wsmResource.getName());
        return;
      }
    }
    fail("Failed to find resource in resource list");
  }
}
