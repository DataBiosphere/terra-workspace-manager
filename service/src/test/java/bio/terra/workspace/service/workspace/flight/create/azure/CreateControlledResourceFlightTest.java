package bio.terra.workspace.service.workspace.flight.create.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateControlledResourceFlightTest extends BaseAzureTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private ControlledResourceService controlledResourceService;

  @Test
  public void createAzureIpControlledResource() throws InterruptedException {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

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
            .workspaceId(workspaceId)
            .resourceId(resourceId)
            .name(getAzureName("ip"))
            .description(getAzureName("ip-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
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
      ControlledAzureIpResource azureIpResource = res.castToAzureIpResource();
      assertEquals(resource, azureIpResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureIpResource", e);
    }
  }

  @Test
  public void createAzureDiskControlledResource() throws InterruptedException {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

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
            .workspaceId(workspaceId)
            .resourceId(resourceId)
            .name(getAzureName("disk"))
            .description(getAzureName("disk-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
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
      ControlledAzureDiskResource azureDiskResource = res.castToAzureDiskResource();
      assertEquals(resource, azureDiskResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureDiskResource", e);
    }
  }

  @Test
  public void createAzureVmControlledResource() throws InterruptedException {
    // Setup workspace and cloud context

    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

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
    // TODO network

    final ApiAzureVmCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .workspaceId(workspaceId)
            .resourceId(resourceId)
            .name(getAzureName("vm"))
            .description(getAzureName("vm-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .vmName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            // TODO network: not used in step currently,
            .networkId(creationParameters.getNetworkId())
            .diskId(diskResource.getResourceId())
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
      ControlledAzureVmResource azureVmResource = res.castToAzureVmResource();
      assertEquals(resource, azureVmResource);
    } catch (Exception e) {
      fail("Failed to cast resource to ControlledAzureVmResource", e);
    }
  }

  private ControlledAzureDiskResource createDisk(
      UUID workspaceId, AuthenticatedUserRequest userRequest) throws InterruptedException {
    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .workspaceId(workspaceId)
            .resourceId(resourceId)
            .name(getAzureName("disk"))
            .description(getAzureName("disk-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
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
            .workspaceId(workspaceId)
            .resourceId(resourceId)
            .name(getAzureName("ip"))
            .description(getAzureName("ip-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
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

  private static String getAzureName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("wsm-integ-%s-%s", tag, id);
  }
}
