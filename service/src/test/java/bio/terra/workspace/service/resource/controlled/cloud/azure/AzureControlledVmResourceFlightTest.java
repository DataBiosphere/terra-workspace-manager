package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.connected.AzureConnectedTestUtils.STAIRWAY_FLIGHT_TIMEOUT;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.AzureVmHelper;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

// @Tag("azureConnected") - this test is tagged at the individual test level
@TestInstance(Lifecycle.PER_CLASS)
public class AzureControlledVmResourceFlightTest extends BaseAzureConnectedTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;
  private Workspace sharedWorkspace;
  private UUID workspaceUuid;
  private ControlledAzureDiskResource diskResource;

  @BeforeAll
  public void setup() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    sharedWorkspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
    workspaceUuid = sharedWorkspace.getWorkspaceId();

    // Create disk
    diskResource = createDisk(workspaceUuid, userRequest);
  }

  @AfterAll
  public void cleanup() {
    // Deleting the workspace will also delete any resources contained in the workspace, including
    // VMs and the resources created during setup.
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Tag("azureConnected")
  @Test
  public void createAndDeleteAzureVmControlledResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String userEmail = userAccessUtils.getDefaultUserEmail();

    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters();

    ControlledAzureVmResource resource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureVmResourceBuilder(
                creationParameters, workspaceUuid, diskResource.getResourceId(), userEmail)
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, diskResource);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    VirtualMachine resolvedVm = getVirtualMachine(creationParameters, computeManager);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourcesFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, resource.getResourceId(), userRequest, resource),
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

  @Tag("azureConnectedPlus")
  @Test
  public void createAndDeleteAzureVmControlledResourceWithCustomScriptExtension()
      throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String userEmail = userAccessUtils.getDefaultUserEmail();

    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParametersWithCustomScriptExtension();

    UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.APPLICATION))
                    .assignedUser(userEmail)
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
            .diskId(diskResource.getResourceId())
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, diskResource);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    VirtualMachine resolvedVm = getVirtualMachine(creationParameters, computeManager);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourcesFlight.class,
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

  @Tag("azureConnected")
  @Test
  public void createVmWithFailureMakeSureNetworkInterfaceIsNotAbandoned()
      throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String userEmail = userAccessUtils.getDefaultUserEmail();

    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getInvalidAzureVmCreationParameters();

    UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource vmResource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.APPLICATION))
                    .applicationId("leo")
                    .assignedUser(userEmail)
                    .iamRole(ControlledResourceIamRole.EDITOR)
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
            .diskId(diskResource.getResourceId())
            .build();

    // Submit a VM creation flight. This flight will fail. It is made intentionally.
    // We need this to test undo step and check if network interface is deleted.
    FlightState vmCreationFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, vmResource, creationParameters),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    // VM flight was failed intentionally
    assertEquals(FlightStatus.ERROR, vmCreationFlightState.getFlightStatus());
    assertThrows(
        bio.terra.workspace.service.resource.exception.ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(workspaceUuid, resourceId));

    ComputeManager computeManager = azureTestUtils.getComputeManager();
    AzureCloudContext azureCloudContext =
        vmCreationFlightState
            .getResultMap()
            .get()
            .get("azureCloudContext", AzureCloudContext.class);
    // validate that VM doesn't exist
    try {
      computeManager
          .virtualMachines()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmResource.getVmName());
      fail("VM should not exist");
    } catch (ManagementException e) {
      assertEquals(404, e.getResponse().getStatusCode());
    }

    assertTrue(vmCreationFlightState.getResultMap().isPresent());
    assertTrue(
        vmCreationFlightState
            .getResultMap()
            .get()
            .containsKey(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY));
    assertTrue(vmCreationFlightState.getResultMap().get().containsKey("azureCloudContext"));

    // validate that our network interface doesn't exist
    String networkInterfaceName =
        vmCreationFlightState
            .getResultMap()
            .get()
            .get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class);
    try {
      computeManager
          .networkManager()
          .networkInterfaces()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), networkInterfaceName);
      fail("Network interface should not exist");
    } catch (ManagementException e) {
      assertEquals(404, e.getResponse().getStatusCode());
    }
  }

  @Tag("azureConnectedPlus")
  @Test
  public void createAndDeleteAzureVmControlledResourceWithEphemeralDisk()
      throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String userEmail = userAccessUtils.getDefaultUserEmail();

    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures
            .getAzureVmCreationParametersWithEphemeralOsDiskAndCustomData();

    UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("vm"))
                    .description(getAzureName("vm-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.APPLICATION))
                    .assignedUser(userEmail)
                    .build())
            .vmName(creationParameters.getName())
            .vmSize(creationParameters.getVmSize())
            .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
            .build();

    // Submit a VM creation flight and verify the resource exists in the workspace.
    createVMResource(workspaceUuid, userRequest, resource, creationParameters);

    // Exercise resource enumeration for the underlying resources.
    // Verify that the resources we created are in the enumeration.
    List<WsmResource> resourceList =
        wsmResourceService.enumerateResources(workspaceUuid, null, null, 0, 100);
    checkForResource(resourceList, resource);

    ComputeManager computeManager = azureTestUtils.getComputeManager();

    VirtualMachine resolvedVm = getVirtualMachine(creationParameters, computeManager);

    // Submit a VM deletion flight.
    FlightState deleteFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourcesFlight.class,
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

  private void createVMResource(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource,
      ApiAzureVmCreationParameters vmCreationParameters)
      throws InterruptedException {
    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        resource,
        WsmResourceType.CONTROLLED_AZURE_VM,
        vmCreationParameters);
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
    ApiAzureDiskCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureDiskCreationParameters();

    ControlledAzureDiskResource resource =
        ControlledAzureResourceFixtures.makeDefaultAzureDiskBuilder(
                creationParameters, workspaceUuid, userAccessUtils.getDefaultUserEmail())
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
}
