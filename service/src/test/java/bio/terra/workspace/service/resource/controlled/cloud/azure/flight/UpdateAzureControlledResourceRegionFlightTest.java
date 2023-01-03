package bio.terra.workspace.service.resource.controlled.cloud.azure.flight;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.AzureConnectedTestUtils;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.TestLandingZoneManager;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(Lifecycle.PER_CLASS)
public class UpdateAzureControlledResourceRegionFlightTest extends BaseAzureConnectedTest {

  private static final String STORAGE_ACCOUNT_REGION = "eastus";
  private static final String LANDING_ZONE_REGION = "centralus";
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private ResourceDao resourceDao;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private AzureConfiguration azureConfig;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private AzureConnectedTestUtils azureUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobApiUtils jobApiUtils;
  @Autowired private JobService jobService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private LandingZoneTestUtils landingZoneTestUtils;
  @Autowired private LandingZoneDao landingZoneDao;
  @Autowired private CrlService crlService;

  private Workspace workspace;
  private UUID workspaceId;
  private String storageAccountName;
  private TestLandingZoneManager testLandingZoneManager;

  @BeforeAll
  public void setUp() throws InterruptedException {
    workspace = azureTestUtils.createWorkspace(workspaceService);
    workspaceId = workspace.getWorkspaceId();

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    // Create cloud context
    azureUtils.createCloudContext(workspaceId, userRequest);
  }

  @AfterAll
  public void cleanUp() {
    workspaceUtils.deleteWorkspaceAndCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void updateResourceWithoutRegionOnly() {
    ControlledResource ip = createIp();
    ControlledResource disk = createDisk();
    ControlledResource network = createNetwork();
    ControlledResource vm =
        createVm(ip.getResourceId(), network.getResourceId(), disk.getResourceId());
    ControlledResource relayNamespace = createRelayNamespace();
    ControlledResource storage = createStorageAccount();
    ControlledResource storageContainer = createStorageContainer(storage.getResourceId());

    List<ControlledResource> emptyList =
        controlledResourceService.updateAzureControlledResourcesRegion();
    assertTrue(emptyList.isEmpty());

    resourceDao.updateControlledResourceRegion(ip.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(disk.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(network.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(vm.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(relayNamespace.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(storage.getResourceId(), /*region=*/ null);
    resourceDao.updateControlledResourceRegion(storageContainer.getResourceId(), /*region=*/ null);

    List<ControlledResource> updatedResource =
        controlledResourceService.updateAzureControlledResourcesRegion();
    assertEquals(7, updatedResource.size());
    ControlledResource updatedIp =
        updatedResource.stream()
            .filter(resource -> ip.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(DEFAULT_AZURE_RESOURCE_REGION, updatedIp.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedNetwork =
        updatedResource.stream()
            .filter(resource -> network.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(
        DEFAULT_AZURE_RESOURCE_REGION, updatedNetwork.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedDisk =
        updatedResource.stream()
            .filter(resource -> disk.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(DEFAULT_AZURE_RESOURCE_REGION, updatedDisk.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedVm =
        updatedResource.stream()
            .filter(resource -> vm.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(DEFAULT_AZURE_RESOURCE_REGION, updatedVm.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedRelayNamespace =
        updatedResource.stream()
            .filter(resource -> relayNamespace.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(
        DEFAULT_AZURE_RESOURCE_REGION, updatedRelayNamespace.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedStorage =
        updatedResource.stream()
            .filter(resource -> storage.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(STORAGE_ACCOUNT_REGION, updatedStorage.getRegion().toLowerCase(Locale.ROOT));
    ControlledResource updatedStorageContainer =
        updatedResource.stream()
            .filter(resource -> storageContainer.getResourceId().equals(resource.getResourceId()))
            .findAny()
            .get();
    assertEquals(
        STORAGE_ACCOUNT_REGION, updatedStorageContainer.getRegion().toLowerCase(Locale.ROOT));
  }

  private ControlledResource createIp() {
    ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();
    ControlledAzureIpResource ipResource =
        ControlledResourceFixtures.makeDefaultAzureIpResource(ipCreationParameters, workspaceId)
            .build();
    return controlledResourceService.createControlledResourceSync(
        ipResource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        ipCreationParameters);
  }

  private ControlledResource createDisk() {
    ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();
    ControlledAzureDiskResource resource =
        ControlledResourceFixtures.makeDefaultAzureDiskBuilder(creationParameters, workspaceId)
            .build();
    return controlledResourceService.createControlledResourceSync(
        resource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        creationParameters);
  }

  private ControlledResource createNetwork() {
    ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    ControlledAzureNetworkResource resource =
        ControlledResourceFixtures.makeDefaultAzureNetworkResourceBuilder(
                creationParameters, workspaceId)
            .build();
    return controlledResourceService.createControlledResourceSync(
        resource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        creationParameters);
  }

  private ControlledResource createVm(
      UUID ipResourceId, UUID networkResourceId, UUID diskResourceId) {
    ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    ControlledAzureVmResource resource =
        ControlledResourceFixtures.makeDefaultControlledAzureVmResourceBuilder(
                creationParameters, workspaceId, ipResourceId, networkResourceId, diskResourceId)
            .build();
    return controlledResourceService.createControlledResourceSync(
        resource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        creationParameters);
  }

  private ControlledResource createRelayNamespace() {
    ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();
    ControlledAzureRelayNamespaceResource resource =
        ControlledResourceFixtures.makeDefaultRelayNamespaceBuilder(creationParameters, workspaceId)
            .build();
    var jobId =
        controlledResourceService.createAzureRelayNamespace(
            resource,
            creationParameters,
            ControlledResourceIamRole.OWNER,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "fake/path",
            userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(jobId);
    JobApiUtils.AsyncJobResult<ControlledAzureRelayNamespaceResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureRelayNamespaceResource.class);

    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      return jobResult.getResult();
    }
    return null;
  }

  private ControlledResource createStorageAccount() {
    ApiAzureStorageCreationParameters accountCreationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    ControlledAzureStorageResource accountResource =
        ControlledResourceFixtures.getAzureStorage(
            workspaceId,
            UUID.randomUUID(),
            accountCreationParameters.getStorageAccountName(),
            accountCreationParameters.getRegion(),
            getAzureName("storage"),
            "storage-account-resource-description");

    // Submit a storage account creation flight and then verify the resource exists in the
    // workspace.
    return controlledResourceService.createControlledResourceSync(
        accountResource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        accountCreationParameters);
  }

  private ControlledResource createStorageContainer(UUID accountResourceId) {
    ApiAzureStorageContainerCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureStorageContainerCreationParameters();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceId,
            accountResourceId,
            UUID.randomUUID(),
            creationParameters.getStorageContainerName(),
            getAzureName("storagecontainer"),
            "storage-container-resource-description");
    return controlledResourceService.createControlledResourceSync(
        containerResource,
        ControlledResourceIamRole.OWNER,
        userAccessUtils.defaultUserAuthRequest(),
        ControlledResourceFixtures.getAzureStorageContainerCreationParameters());
  }
}
