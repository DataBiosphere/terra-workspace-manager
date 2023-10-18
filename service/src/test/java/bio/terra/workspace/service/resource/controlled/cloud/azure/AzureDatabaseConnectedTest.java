package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.connected.AzureConnectedTestUtils.STAIRWAY_FLIGHT_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace.ControlledAzureKubernetesNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.models.Identity;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Database;
import io.kubernetes.client.openapi.ApiException;
import java.util.List;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Tag("azureConnected")
@TestInstance(Lifecycle.PER_CLASS)
public class AzureDatabaseConnectedTest extends BaseAzureConnectedTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private LandingZoneApiDispatch landingZoneApiDispatch;
  @Autowired private SamService samService;
  @Autowired private KubernetesClientProvider kubernetesClientProvider;
  @Autowired private AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  @Autowired private JobService jobService;

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

  @Test
  public void createDbDummyTest() throws InterruptedException {
    azureDatabaseUtilsRunner.createDatabaseWithDbRole(
            azureTestUtils.getAzureCloudContext(), workspaceUuid, "createdb-test-pod", "workflowcloningtest");
  }

  @Test
  public void pgDumpDatabaseTest() throws InterruptedException {
    // should I use `createDatabase` (without db role) instead?
    azureDatabaseUtilsRunner.pgDumpDatabase(
        azureTestUtils.getAzureCloudContext(), workspaceUuid, "pgdump-test-pod", "workflowcloningtest");
  }

  @Test
  public void createAndDeleteAzureManagedIdAndDatabase() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    var uamiResource = createManagedIdentity(userRequest);
    var dbResource = createDatabase(userRequest, uamiResource, "default");

    // Verify that the resources we created
    var resourceList = wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, uamiResource);
    checkForResource(resourceList, dbResource);

    // wait for azure to sync then make sure the resources actually exist
    Awaitility.await()
        .atMost(1, TimeUnit.MINUTES)
        .pollInterval(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
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
            });

    azureDatabaseUtilsRunner.testDatabaseConnect(
        azureTestUtils.getAzureCloudContext(),
        workspaceUuid,
        "default",
        "test-connect",
        getDbServerName(),
        dbResource.getDatabaseName(),
        uamiResource.getManagedIdentityName());

    deleteDatabase(userRequest, dbResource);
    deleteManagedIdentity(userRequest, uamiResource);
  }

  @Test
  public void createAndDeleteK8sNamespaceWithDatabase() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    var uamiResource = createManagedIdentity(userRequest);
    var dbResource = createDatabase(userRequest, uamiResource);
    var k8sResource = createKubernetesNamespace(userRequest, uamiResource, List.of(dbResource));

    samService.grantWorkspaceRole(
        workspaceUuid,
        userAccessUtils.defaultUserAuthRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    var privateK8sResource =
        createPrivateKubernetesNamespace(
            userAccessUtils.secondUserAuthRequest(), List.of(dbResource));

    // Verify that the resources we created
    var resourceList = wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, uamiResource);
    checkForResource(resourceList, dbResource);
    checkForResource(resourceList, k8sResource);
    checkForResource(resourceList, privateK8sResource);

    // wait for azure to sync then make sure the resources actually exist
    Awaitility.await()
        .atMost(1, TimeUnit.MINUTES)
        .pollInterval(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
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
            });

    azureDatabaseUtilsRunner.testDatabaseConnect(
        azureTestUtils.getAzureCloudContext(),
        workspaceUuid,
        k8sResource.getKubernetesNamespace(),
        "test-connect",
        getDbServerName(),
        dbResource.getDatabaseName(),
        k8sResource.getKubernetesServiceAccount());

    azureDatabaseUtilsRunner.testDatabaseConnect(
        azureTestUtils.getAzureCloudContext(),
        workspaceUuid,
        privateK8sResource.getKubernetesNamespace(),
        "test-private-connect",
        getDbServerName(),
        dbResource.getDatabaseName(),
        privateK8sResource.getKubernetesServiceAccount());

    removeSecondUserFromWorkspace();
    // after being removed from the workspace, the second user's k8s namespace should not be able to
    // connect to the database
    assertThrows(
        RetryException.class,
        () ->
            azureDatabaseUtilsRunner.testDatabaseConnect(
                azureTestUtils.getAzureCloudContext(),
                workspaceUuid,
                privateK8sResource.getKubernetesNamespace(),
                "test-private-connect",
                getDbServerName(),
                dbResource.getDatabaseName(),
                privateK8sResource.getKubernetesServiceAccount()));

    deleteKubernetesNamespace(userRequest, privateK8sResource);
    deleteKubernetesNamespace(userRequest, k8sResource);
    deleteDatabase(userRequest, dbResource);
    deleteManagedIdentity(userRequest, uamiResource);
  }

  private void removeSecondUserFromWorkspace() throws InterruptedException {
    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputParameters.put(
        WorkspaceFlightMapKeys.USER_TO_REMOVE, userAccessUtils.getSecondUserEmail());
    inputParameters.put(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, WsmIamRole.WRITER.name());
    // Auth info comes from default user, as they are the ones "making this request"
    inputParameters.put(
        JobMapKeys.AUTH_USER_INFO.getKeyName(), userAccessUtils.defaultUserAuthRequest());
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            RemoveUserFromWorkspaceFlight.class,
            inputParameters,
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
  }

  private void deleteKubernetesNamespace(
      AuthenticatedUserRequest userRequest, ControlledAzureKubernetesNamespaceResource k8sResource)
      throws InterruptedException {
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        k8sResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        k8sResource.getKubernetesNamespace(),
        null);
    assertNamespaceDeleted(k8sResource);
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

  private void assertNamespaceDeleted(ControlledAzureKubernetesNamespaceResource namespace) {
    var clusterResource =
        landingZoneApiDispatch
            .getSharedKubernetesCluster(
                new BearerToken(samService.getWsmServiceAccountToken()), landingZoneId)
            .orElseThrow();
    var apiClient =
        kubernetesClientProvider.createCoreApiClient(
            azureTestUtils.getContainerServiceManager(),
            azureTestUtils.getAzureCloudContext(),
            clusterResource);

    var notFound =
        assertThrows(
            ApiException.class,
            () -> {
              apiClient.readNamespace(namespace.getKubernetesNamespace(), null);
            });
    assertEquals(404, notFound.getCode());

    var managedIdentity =
        namespace
            .getAssignedUser()
            .map(
                email -> {
                  try {
                    return samService.getOrCreateUserManagedIdentityForUser(
                        email,
                        azureTestUtils.getAzureCloudContext().getAzureSubscriptionId(),
                        azureTestUtils.getAzureCloudContext().getAzureTenantId(),
                        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId());
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                })
            .orElse(namespace.getManagedIdentity());

    var fedCredsNotFound =
        assertThrows(
            ManagementException.class,
            () ->
                azureTestUtils
                    .getMsiManager()
                    .identities()
                    .manager()
                    .serviceClient()
                    .getFederatedIdentityCredentials()
                    .get(
                        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                        managedIdentity,
                        namespace.getKubernetesNamespace()));
    assertEquals(
        Optional.of(HttpStatus.NOT_FOUND),
        AzureManagementExceptionUtils.getHttpStatus(fedCredsNotFound));
  }

  @NotNull
  private BiFunction<String, String, Database> getDatabaseFunction() {
    String dbServerName = getDbServerName();
    return (resourceGroup, resourceName) ->
        azureTestUtils
            .getPostgreSqlManager()
            .databases()
            .get(resourceGroup, dbServerName, resourceName);
  }

  @NotNull
  private String getDbServerName() {
    return landingZoneApiDispatch
        .getSharedDatabase(new BearerToken(samService.getWsmServiceAccountToken()), landingZoneId)
        .map(
            r -> {
              var parts = r.getResourceId().split("/");
              return parts[parts.length - 1];
            })
        .orElseThrow();
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

  private ControlledAzureKubernetesNamespaceResource createKubernetesNamespace(
      AuthenticatedUserRequest userRequest,
      ControlledAzureManagedIdentityResource uamiResource,
      List<ControlledAzureDatabaseResource> databases)
      throws InterruptedException {
    var k8sNamespaceCreationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            uamiResource.getName(),
            databases.stream().map(ControlledAzureDatabaseResource::getName).toList());

    var k8sNamespaceResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                k8sNamespaceCreationParameters, workspaceUuid)
            .build();

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        k8sNamespaceResource,
        WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE,
        k8sNamespaceCreationParameters);
    return k8sNamespaceResource;
  }

  private ControlledAzureKubernetesNamespaceResource createPrivateKubernetesNamespace(
      AuthenticatedUserRequest userRequest, List<ControlledAzureDatabaseResource> databases)
      throws InterruptedException {
    var k8sNamespaceCreationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, databases.stream().map(ControlledAzureDatabaseResource::getName).toList());

    var k8sNamespaceResource =
        ControlledAzureResourceFixtures
            .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
                k8sNamespaceCreationParameters, workspaceUuid, userRequest.getEmail(), null)
            .build();

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        k8sNamespaceResource,
        WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE,
        k8sNamespaceCreationParameters);
    return k8sNamespaceResource;
  }

  private ControlledAzureDatabaseResource createDatabase(
      AuthenticatedUserRequest userRequest, ControlledAzureManagedIdentityResource uamiResource)
      throws InterruptedException {
    var dbCreationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            uamiResource.getName(), true);

    var dbResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
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
