package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.storage.StorageManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CreateAndDeleteAzureControlledResourceFlightTest extends BaseAzureTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(15);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;

  private void createCloudContext(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
          throws InterruptedException {
    FlightState createAzureContextFlightState = StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(workspaceService.getAuthorizedAzureCloudContext(workspaceUuid, userRequest).isPresent());
  }

  private void createVMResource(UUID workspaceUuid, AuthenticatedUserRequest userRequest, ControlledResource resource,
                                ApiAzureVmCreationParameters vmCreationParameters) throws InterruptedException {
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_VM, vmCreationParameters);

  }

  private void createResource(UUID workspaceUuid, AuthenticatedUserRequest userRequest, ControlledResource resource,
                              WsmResourceType resourceType) throws InterruptedException {
    createResource(workspaceUuid, userRequest, resource, resourceType, null);
  }

  private void createResource(UUID workspaceUuid, AuthenticatedUserRequest userRequest, ControlledResource resource,
                              WsmResourceType resourceType, ApiAzureVmCreationParameters vmCreationParameters)
          throws InterruptedException {

    FlightState flightState = StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                    workspaceUuid, userRequest, resource, vmCreationParameters),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
            controlledResourceService.getControlledResource(workspaceUuid, resource.getResourceId(), userRequest);

    try {
      var castResource = res.castByEnum(resourceType);
      assertEquals(resource, castResource);
    } catch (Exception e) {
      fail(String.format("Failed to cast resource to %s", resourceType), e);
    }
  }

  @Test
  public void createAzureIpControlledResource() throws InterruptedException {
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("ip"))
                    .description(getAzureName("ip-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .ipName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .build();

    // Submit an IP creation flight and verify the instance is created.
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_IP);
  }

  @Test
  public void createAndDeleteAzureRelayNamespaceControlledResource() throws InterruptedException {
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    final UUID resourceId = UUID.randomUUID();
    ControlledAzureRelayNamespaceResource resource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("ip"))
                    .description(getAzureName("ip-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .namespaceName(creationParameters.getNamespaceName())
            .region(creationParameters.getRegion())
            .build();

    // Submit a relay creation flight and verify the resource exists in the workspace.
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);

    // Submit a relay deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourceFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, resourceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, deleteFlightState.getFlightStatus());

    TimeUnit.SECONDS.sleep(5);
    RelayManager manager = azureTestUtils.getRelayManager();
    ManagementException exception =
        assertThrows(
            ManagementException.class,
            () ->
                manager
                    .namespaces()
                    .getByResourceGroup(
                        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                        resource.getNamespaceName()));
    // We see both ResourceNotFound and NotFound in the code field
    assertTrue(exception.getValue().getCode().contains("NotFound"));
  }

  @Test
  public void createAndDeleteAzureStorageResource() throws InterruptedException {
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    final ApiAzureStorageCreationParameters creationParameters =
            ControlledResourceFixtures.getAzureStorageCreationParameters();

    final UUID resourceId = UUID.randomUUID();
    ControlledAzureStorageResource resource =
            ControlledAzureStorageResource.builder()
                    .common(
                            ControlledResourceFields.builder()
                                    .workspaceUuid(workspaceUuid)
                                    .resourceId(resourceId)
                                    .name(getAzureName("rs"))
                                    .description(getAzureName("rs-desc"))
                                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                                    .build())
                    .storageAccountName(creationParameters.getName())
                    .region(creationParameters.getRegion())
                    .build();

    // Submit a storage account creation flight and then verify the resource exists in the workspace.
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    // Submit a storage account deletion flight.
    FlightState deleteFlightState =
            StairwayTestUtils.blockUntilFlightCompletes(
                    jobService.getStairway(),
                    DeleteControlledResourceFlight.class,
                    azureTestUtils.deleteControlledResourceInputParameters(
                            workspaceUuid, resourceId, userRequest, resource),
                    STAIRWAY_FLIGHT_TIMEOUT,
                    null);
    assertEquals(FlightStatus.SUCCESS, deleteFlightState.getFlightStatus());

    TimeUnit.SECONDS.sleep(5);
    StorageManager manager = azureTestUtils.getStorageManager();
    ManagementException exception =
            assertThrows(
                    ManagementException.class,
                    () ->
                            manager.storageAccounts().getByResourceGroup(
                                    azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                                    resource.getStorageAccountName()));
    // We see both ResourceNotFound and NotFound in the code field
    assertTrue(exception.getValue().getCode().contains("NotFound"));
  }

  @Test
  public void createAzureDiskControlledResource() throws InterruptedException {
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("disk"))
                    .description(getAzureName("disk-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .diskName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .size(creationParameters.getSize())
            .build();

    // Submit a Disk creation flight and verify the resource exists in the workspace.
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_DISK);
  }

  @Test
  public void createAndDeleteAzureVmControlledResource() throws InterruptedException {
    // Setup workspace and cloud context
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    // Create ip
    ControlledAzureIpResource ipResource = createIp(workspaceUuid, userRequest);

    // Create disk
    ControlledAzureDiskResource diskResource = createDisk(workspaceUuid, userRequest);

    // Create network
    ControlledAzureNetworkResource networkResource = createNetwork(workspaceUuid, userRequest);

    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
            .region(creationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100, userRequest);
    checkForResource(resourceList, ipResource);
    checkForResource(resourceList, diskResource);
    checkForResource(resourceList, networkResource);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    VirtualMachine vmTemp = null;
    var retries = 20;
    while (vmTemp == null) {
      try {
        retries = retries - 1;

        if (retries >= 0) {
          vmTemp =
              computeManager
                  .virtualMachines()
                  .getByResourceGroup(
                      azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                      creationParameters.getName());
        } else
          throw new RuntimeException(
              String.format("%s is not created in time in Azure", creationParameters.getName()));
      } catch (com.azure.core.exception.HttpResponseException ex) {
        if (ex.getResponse().getStatusCode() == 404) Thread.sleep(10000);
        else throw ex;
      }
    }
    final VirtualMachine resolvedVm = vmTemp;

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourceFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, resourceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, deleteFlightState.getFlightStatus());

    Thread.sleep(10000);
    resolvedVm
        .networkInterfaceIds()
        .forEach(
            nic ->
                assertThrows(
                    com.azure.core.exception.HttpResponseException.class,
                    () -> computeManager.networkManager().networks().getById(nic)));
    assertThrows(
        com.azure.core.exception.HttpResponseException.class,
        () -> computeManager.disks().getById(resolvedVm.osDiskId()));
  }

  @Test
  public void createAndDeleteAzureVmControlledResourceWithCustomScriptExtension()
      throws InterruptedException {
    // Setup workspace and cloud context
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    // Create ip
    ControlledAzureIpResource ipResource = createIp(workspaceUuid, userRequest);

    // Create disk
    ControlledAzureDiskResource diskResource = createDisk(workspaceUuid, userRequest);

    // Create network
    ControlledAzureNetworkResource networkResource = createNetwork(workspaceUuid, userRequest);

    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParametersWithCustomScriptExtension();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
            .region(creationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100, userRequest);
    checkForResource(resourceList, ipResource);
    checkForResource(resourceList, diskResource);
    checkForResource(resourceList, networkResource);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    final VirtualMachine resolvedVm = getVirtualMachine(creationParameters, computeManager);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourceFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, resourceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, deleteFlightState.getFlightStatus());

    Thread.sleep(10000);
    resolvedVm
        .networkInterfaceIds()
        .forEach(
            nic ->
                assertThrows(
                    com.azure.core.exception.HttpResponseException.class,
                    () -> computeManager.networkManager().networks().getById(nic)));
    assertThrows(
        com.azure.core.exception.HttpResponseException.class,
        () -> computeManager.disks().getById(resolvedVm.osDiskId()));
  }

  @Test
  public void createAndDeleteAzureVmControlledResourceWithCustomScriptExtensionWithNoPublicIp()
      throws InterruptedException {
    // Setup workspace and cloud context
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    // Create disk
    ControlledAzureDiskResource diskResource = createDisk(workspaceUuid, userRequest);

    // Create network
    ControlledAzureNetworkResource networkResource = createNetwork(workspaceUuid, userRequest);

    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParametersWithCustomScriptExtension();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
            .region(creationParameters.getRegion())
            // .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100, userRequest);
    // checkForResource(resourceList, ipResource);
    checkForResource(resourceList, diskResource);
    checkForResource(resourceList, networkResource);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    final VirtualMachine resolvedVm = getVirtualMachine(creationParameters, computeManager);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourceFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, resourceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, deleteFlightState.getFlightStatus());

    Thread.sleep(10000);
    resolvedVm
        .networkInterfaceIds()
        .forEach(
            nic ->
                assertThrows(
                    com.azure.core.exception.HttpResponseException.class,
                    () -> computeManager.networkManager().networks().getById(nic)));
    assertThrows(
        com.azure.core.exception.HttpResponseException.class,
        () -> computeManager.disks().getById(resolvedVm.osDiskId()));
  }

  private VirtualMachine getVirtualMachine(
      ApiAzureVmCreationParameters creationParameters, ComputeManager computeManager)
      throws InterruptedException {
    var retries = 20;
    VirtualMachine vmTemp = null;
    while (vmTemp == null) {
      try {
        retries = retries - 1;

        if (retries >= 0) {
          vmTemp =
              computeManager
                  .virtualMachines()
                  .getByResourceGroup(
                      azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                      creationParameters.getName());
        } else
          throw new RuntimeException(
              String.format("%s is not created in time in Azure", creationParameters.getName()));
      } catch (com.azure.core.exception.HttpResponseException ex) {
        if (ex.getResponse().getStatusCode() == 404) Thread.sleep(10000);
        else throw ex;
      }
    }
    return vmTemp;
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

  private ControlledAzureDiskResource createDisk(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) throws InterruptedException {
    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("disk"))
                    .description(getAzureName("disk-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .diskName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .size(creationParameters.getSize())
            .build();

    // Submit a Disk creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    return resource;
  }

  ControlledAzureIpResource createIp(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    final ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("ip"))
                    .description(getAzureName("ip-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .ipName(ipCreationParameters.getName())
            .region(ipCreationParameters.getRegion())
            .build();

    // Submit an IP creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    return resource;
  }

  private ControlledAzureNetworkResource createNetwork(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("network"))
                    .description(getAzureName("network-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .networkName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .subnetName(creationParameters.getSubnetName())
            .addressSpaceCidr(creationParameters.getAddressSpaceCidr())
            .subnetAddressCidr(creationParameters.getSubnetAddressCidr())
            .build();

    // Submit a Disk creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    return resource;
  }

  private static String getAzureName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("wsm-integ-%s-%s", tag, id);
  }

  @Test
  public void createAzureNetworkControlledResource() throws InterruptedException {
    UUID workspaceUuid = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createCloudContext(workspaceUuid, userRequest);

    final ApiAzureNetworkCreationParameters creationParams =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name("testNetwork")
                    .description("testDesc")
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .networkName(creationParams.getName())
            .region(creationParams.getRegion())
            .subnetName(creationParams.getSubnetName())
            .addressSpaceCidr(creationParams.getAddressSpaceCidr())
            .subnetAddressCidr(creationParams.getSubnetAddressCidr())
            .build();

    // Submit a Network creation flight and verify the resource exists in the workspace.
    createResource(workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_NETWORK);
  }
}
