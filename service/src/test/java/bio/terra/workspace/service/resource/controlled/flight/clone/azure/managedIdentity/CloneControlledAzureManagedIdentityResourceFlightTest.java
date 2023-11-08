package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("azureConnected")
public class CloneControlledAzureManagedIdentityResourceFlightTest extends BaseAzureConnectedTest {

  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;

  private Workspace sourceWorkspace;
  private Workspace destinationWorkspace;

  @BeforeAll
  public void setup() throws InterruptedException {
    sourceWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
    destinationWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterAll
  public void cleanup() {
    workspaceService.deleteWorkspace(sourceWorkspace, userAccessUtils.defaultUserAuthRequest());
    workspaceService.deleteWorkspace(
        destinationWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  void cloneControlledAzureManagedIdentity() throws InterruptedException {

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID destinationResourceId = UUID.randomUUID();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();

    var sourceResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, sourceWorkspace.workspaceId())
            .managedIdentityName("idfoobar")
            .build();

    azureUtils.createResource(
        sourceWorkspace.workspaceId(),
        userRequest,
        sourceResource,
        WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY,
        creationParameters);

    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceResource);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        CloningInstructions.COPY_DEFINITION.name());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, sourceResource.getName());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspace.workspaceId());

    var result =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CloneControlledAzureManagedIdentityResourceFlight.class,
            inputs,
            Duration.ofMinutes(1),
            null);

    ClonedAzureResource resultIdentity =
        result
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class);
    assertNotNull(resultIdentity);
    assertEquals(
        resultIdentity.effectiveCloningInstructions(), CloningInstructions.COPY_DEFINITION);
    assertEquals(
        resultIdentity.managedIdentity().getWorkspaceId(), destinationWorkspace.workspaceId());
    assertEquals(resultIdentity.managedIdentity().getResourceId(), destinationResourceId);
    assertEquals(resultIdentity.managedIdentity().getName(), sourceResource.getName());
    assertEquals(sourceResource.getManagedIdentityName(), "idfoobar");
    assertTrue(resultIdentity.managedIdentity().getManagedIdentityName().startsWith("id"));
    assertNotEquals(
        resultIdentity.managedIdentity().getManagedIdentityName(),
        sourceResource.getManagedIdentityName());
    assertEquals(result.getFlightStatus(), FlightStatus.SUCCESS);

    ControlledAzureManagedIdentityResource resourceInWorkspace =
        controlledResourceService
            .getControlledResource(destinationWorkspace.workspaceId(), destinationResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
    assertNotNull(resourceInWorkspace);
    assertEquals(
        resultIdentity.managedIdentity().attributesToJson(),
        resourceInWorkspace.attributesToJson());
    assertEquals(
        resultIdentity.managedIdentity().getManagedIdentityName(),
        resourceInWorkspace.getManagedIdentityName());
    assertEquals(
        resultIdentity.managedIdentity().getWsmControlledResourceFields(),
        resourceInWorkspace.getWsmControlledResourceFields());
  }
}
