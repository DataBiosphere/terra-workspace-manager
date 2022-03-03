package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateAndDeleteAzureControlledResourceFlightTest extends BaseAzureTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(15);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;

  @Test
  public void createAzureIpControlledResource() throws InterruptedException {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Cloud context needs to be created first
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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

    // Submit an IP creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);

    try {
      var azureIpResource = res.castByEnum(WsmResourceType.CONTROLLED_AZURE_IP);
      assertEquals(resource, azureIpResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureIpResource", e);
    }
  }

  @Test
  public void createAndDeleteAzureRelayNamespaceControlledResource() throws InterruptedException {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Cloud context needs to be created first
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    final UUID resourceId = UUID.randomUUID();
    ControlledAzureRelayNamespaceResource resource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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

    // Submit an IP creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);

    var relayNamespaceResource = res.castByEnum(WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);
    assertEquals(resource, relayNamespaceResource);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourceFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceId, resourceId, userRequest, resource),
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
  public void createAzureDiskControlledResource() throws InterruptedException {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Cloud context needs to be created first
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);

    try {
      ControlledAzureDiskResource azureDiskResource =
          res.castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
      assertEquals(resource, azureDiskResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureDiskResource", e);
    }
  }

  @Test
  public void createAndDeleteAzureVmControlledResource() throws InterruptedException {
    // Setup workspace and cloud context
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Cloud context needs to be created first
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    // Create ip
    ControlledAzureIpResource ipResource = createIp(workspaceId, userRequest);

    // Create disk
    ControlledAzureDiskResource diskResource = createDisk(workspaceId, userRequest);

    // Create network
    ControlledAzureNetworkResource networkResource = createNetwork(workspaceId, userRequest);

    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImageUri(creationParameters.getVmImageUri())
            .region(creationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    // Submit a VM creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);

    try {
      ControlledAzureVmResource azureVmResource =
          res.castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
      assertEquals(resource, azureVmResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureVmResource", e);
    }

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceId, null, null, 0, 100, userRequest);
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
                workspaceId, resourceId, userRequest, resource),
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
      UUID workspaceId, AuthenticatedUserRequest userRequest) throws InterruptedException {
    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    return resource;
  }

  ControlledAzureIpResource createIp(UUID workspaceId, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    final ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    return resource;
  }

  private ControlledAzureNetworkResource createNetwork(
      UUID workspaceId, AuthenticatedUserRequest userRequest) throws InterruptedException {
    final ApiAzureNetworkCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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
                workspaceId, userRequest, resource),
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
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Cloud context needs to be created first
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateAzureContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceId, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(
        workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest).isPresent());

    final ApiAzureNetworkCreationParameters creationParams =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceId(workspaceId)
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

    // Submit a Network creation flight.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceId, userRequest, resource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);

    try {
      ControlledAzureNetworkResource azureNetworkResource =
          res.castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);
      assertEquals(resource, azureNetworkResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureNetworkResource", e);
    }
  }
}
